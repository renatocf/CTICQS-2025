package core.services

import core.domain.enums.{SubwalletType, TransactionStatus, TransactionType, WalletType}
import core.domain.model.{CreateTransactionRequest, InvestmentMovementRequest}
import cats.implicits._
import core.errors.{InvestmentServiceError, InvestmentServiceInternalError, ProcessTransactionFailed}
import ports.{InvestmentPolicyDatabase, TransactionDatabase, TransactionFilter, WalletFilter, WalletsDatabase}


class InvestmentService(transactionsRepo: TransactionDatabase, walletsRepo: WalletsDatabase, investmentPolicyRepo: InvestmentPolicyDatabase, transactionsService: TransactionsService) {
   def executeMovementWithInvestmentPolicy(request: InvestmentMovementRequest): Either[InvestmentServiceError, Unit] = {
    val allocationStrategy = request.investmentPolicy.allocationStrategy.toList

    allocationStrategy.traverse {
        case (subwalletType, percentage) =>
          val (originatorSubwalletType, beneficiaryWalletId, beneficiarySubwalletType) = request.transactionType match {
            case TransactionType.Hold => (subwalletType, None, None)
            case TransactionType.TransferFromHold =>
              for {
                walletSubwalletType <- request.walletSubwalletType.toRight(InvestmentServiceInternalError(s"Missing wallet subwallet tyoe"))
              } yield (walletSubwalletType, request.targetWalletId, subwalletType)
            case _ => InvestmentServiceInternalError(s"Unsupported transaction type")
          }

          val createTransactionRequest = CreateTransactionRequest(
            amount = request.amount * percentage,
            batchId = Some(request.idempotencyKey),
            idempotencyKey = s"${request.idempotencyKey}_$subwalletType",
            originatorWalletId = request.walletId,
            originatorSubwalletType = originatorSubwalletType,
            beneficiaryWalletId = beneficiaryWalletId,
            beneficiarySubwalletType = beneficiarySubwalletType,
            transactionType = request.transactionType,
          )

          val transaction = transactionsService.create(createTransactionRequest)

          transactionsService.process(transaction).left.map { e =>
            transactionsService.failBatch(request.idempotencyKey)
            ProcessTransactionFailed(s"Failed to process transaction ${transaction.id}: ${e.message}")
          }
      }
      .map { processTransactionTuples =>
        processTransactionTuples.foreach { tuple =>
          transactionsService.execute(tuple)
        }
      }
  }

  def buyFunds(): Unit = {
    transactionsRepo
      .find(
        TransactionFilter(
          status = Some(TransactionStatus.Processing),
          subwalletType = Some(List(SubwalletType.RealMoney))
        )
      )
      .foreach(investmentTransaction => {
          for {
            wallet <-
              walletsRepo
                .findById(investmentTransaction.originatorWalletId)
                .toRight(InvestmentServiceInternalError(s"Wallet ${investmentTransaction.originatorWalletId} not found"))
            investmentPolicy <-
              investmentPolicyRepo
                .findById(wallet.policyId)
                .toRight(InvestmentServiceInternalError(s"Investment policy ${wallet.policyId} not found"))
            investmentWallet <-
              walletsRepo
                .find(WalletFilter(
                  customerId = Some(wallet.customerId),
                  walletType = Some(WalletType.Investment)
                ))
                .headOption
                .toRight(InvestmentServiceInternalError(s"Investment wallet not found for ${wallet.customerId}"))
          } yield {
            executeMovementWithInvestmentPolicy(InvestmentMovementRequest(
              amount = investmentTransaction.amount,
              idempotencyKey = investmentTransaction.idempotencyKey,
              walletId = investmentTransaction.originatorWalletId,
              targetWalletId = Some(investmentWallet.id),
              investmentPolicy = investmentPolicy,
              transactionType = TransactionType.TransferFromHold
            )).left.map(_ => {
              // fail the investment transaction gracefully
              transactionsService.releaseHold(investmentTransaction)
              transactionsService.updateStatus(investmentTransaction.id, TransactionStatus.Failed)
            })
          }
      })
  }

  def sellFunds(): Unit = {
    transactionsRepo
      .find(
        TransactionFilter(
          status = Some(TransactionStatus.Processing),
          subwalletType =
            Some(
              List(
                SubwalletType.Stock,
                SubwalletType.Bonds,
                SubwalletType.RealEstate,
                SubwalletType.Cryptocurrency,
              )
            )
        )
      )
      .foreach(liquidationTransaction => {
        for {
          wallet <-
            walletsRepo
              .findById(liquidationTransaction.originatorWalletId)
              .toRight(InvestmentServiceInternalError(s"Wallet ${liquidationTransaction.originatorWalletId} not found"))
          realMoneyWallet <-
            walletsRepo
              .find(WalletFilter(
                customerId = Some(wallet.customerId),
                walletType = Some(WalletType.RealMoney)
              ))
              .headOption
              .toRight(InvestmentServiceInternalError(s"Real money wallet not found for ${wallet.customerId}"))
        } yield {
          val createTransactionRequest = CreateTransactionRequest(
            amount = liquidationTransaction.amount,
            idempotencyKey = liquidationTransaction.idempotencyKey,
            originatorWalletId = liquidationTransaction.originatorWalletId,
            originatorSubwalletType = liquidationTransaction.originatorSubwalletType,
            beneficiaryWalletId = Some(realMoneyWallet.id),
            beneficiarySubwalletType = Some(SubwalletType.RealMoney),
            transactionType = TransactionType.TransferFromHold,
          )

          val transaction = transactionsService.create(createTransactionRequest)

          transactionsService
            .process(transaction)
            .left
            .map { e =>
              // fail transfer from hold
              transactionsService.updateStatus(transaction.id, TransactionStatus.Failed)
              // fail liquidation transfer
              transactionsService.releaseHold(liquidationTransaction)
              transactionsService.updateStatus(liquidationTransaction.id, TransactionStatus.Failed)
              ProcessTransactionFailed(s"Failed to process transaction ${transaction.id}: ${e.message}")
            }
            .flatMap { processTransactionTuple =>
              transactionsService.execute(processTransactionTuple)
            }
        }
      })
  }
}
