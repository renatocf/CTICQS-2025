package core.services

import core.domain.entities.{Transaction, Wallet}
import core.domain.enums.*
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.model.*
import core.errors.{InvestmentFailedError, LiquidationFailedError}
import ports.{InvestmentPolicyDatabase, WalletFilter, WalletsDatabase}

class WalletsService(walletsRepo: WalletsDatabase, investmentPolicyRepo: InvestmentPolicyDatabase, ledgerService: LedgerService, transactionsService: TransactionsService, investmentService: InvestmentService) {
  def getAvailableBalance(wallet: Wallet): BigDecimal = {
    val ledgerQuery = wallet.walletType match {
      case WalletType.RealMoney =>
        List(
          LedgerQuery(
            subwalletType = SubwalletType.RealMoney,
            balanceType = BalanceType.Available
          )
        )
      case WalletType.Investment =>
        List(
          LedgerQuery(
            subwalletType = SubwalletType.Bonds,
            balanceType = BalanceType.Available,
          ),
          LedgerQuery(
            subwalletType = SubwalletType.Stock,
            balanceType = BalanceType.Available,
          ),
          LedgerQuery(
            subwalletType = SubwalletType.RealEstate,
            balanceType = BalanceType.Available,
          ),
          LedgerQuery(
            subwalletType = SubwalletType.Cryptocurrency,
            balanceType = BalanceType.Available,
          )
        )
      case WalletType.EmergencyFunds =>
        List(
          LedgerQuery(
            subwalletType = SubwalletType.EmergencyFunds,
            balanceType = BalanceType.Available
          )
        )
    }

    ledgerService.getBalance(wallet.id, ledgerQuery)
  }
  
  def getSubwalletPendingBalance(walletId: String, subwalletType: SubwalletType): BigDecimal = {
    ledgerService.getBalance(
      walletId,
      List(
        LedgerQuery(
          subwalletType = subwalletType,
          balanceType = BalanceType.Holding
        )
      )
    )
  }

  def invest(request: InvestmentRequest): Either[InvestmentFailedError, Transaction] = {
    val wallets = 
      walletsRepo.find(
        WalletFilter(
          customerId = Some(request.customerId), 
          walletType = Some(WalletType.RealMoney)
        )
      )

    wallets match {
      case List(wallet) =>
        for {
          transaction <- transactionsService.create(
            CreateTransactionRequest(
              amount = request.amount,
              idempotencyKey = request.idempotencyKey,
              originatorWalletId = wallet.id,
              originatorSubwalletType = SubwalletType.RealMoney,
              transactionType = TransactionType.Hold
            )
          ).left.map { e =>
            InvestmentFailedError(e.message)
          }
          
          processTransactionTuple <- 
            transactionsService.process(transaction).left.map { e =>
              transactionsService.updateStatus(transaction.id, TransactionStatus.Failed)
              InvestmentFailedError(e.message)
            }
            
          executedTransaction <- 
            transactionsService.execute(processTransactionTuple).left.map(e => 
            InvestmentFailedError(e.message)
          )
        } yield executedTransaction

      case _ =>
        Left(InvestmentFailedError(s"None or multiple wallets found for customer ${request.customerId}"))
    }
  }

  def liquidate(request: LiquidationRequest): Either[LiquidationFailedError, Unit] = {
    val wallets = walletsRepo.find(WalletFilter(customerId = Some(request.customerId), walletType = Some(WalletType.Investment)))

    wallets match {
      case List(wallet) =>
          for {
            policyId <- wallet.policyId.toRight(LiquidationFailedError(s"Wallet ${wallet.id} has no policyId"))
            
            investmentPolicy <-
              investmentPolicyRepo
                .findById(policyId)
                .toRight(LiquidationFailedError(s"Investment policy ${wallet.policyId} not found"))
            
            _ <- investmentService.executeMovementWithInvestmentPolicy(MovementRequest(
                  amount = request.amount,
                  idempotencyKey = request.idempotencyKey,
                  walletId = wallet.id,
                  investmentPolicy = investmentPolicy,
                  transactionType = TransactionType.Hold
                )).left.map(e => LiquidationFailedError(e.message))
          } yield ()
      case _ => Left(LiquidationFailedError(s"None or multiple wallets found for customer ${request.customerId}"))
    }
  }
}
