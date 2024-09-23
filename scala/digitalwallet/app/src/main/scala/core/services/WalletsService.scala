package core.services

import core.domain.entities.{Transaction, Wallet}
import core.domain.enums.{BalanceType, SubwalletType, TransactionStatus, TransactionType, WalletType}
import core.domain.model.{InvestmentRequest, LedgerQuery, ProcessTransactionRequest}
import core.errors.{InvestmentFailedError, WalletNotFound}
import ports.{WalletFilter, WalletsDatabase}

class WalletsService(walletsRepo: WalletsDatabase, ledgerService: LedgerService, transactionsService: TransactionsService) {
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

  def invest(request: InvestmentRequest): Either[InvestmentFailedError, Transaction] = {
    val wallets = walletsRepo.find(WalletFilter(customerId = Some(request.customerId), walletType = Some(WalletType.RealMoney)))

    wallets match {
      case _ => Left(WalletNotFound(s"Wallet not found for customer ${request.customerId}"))
      case List(wallet) =>
        val createTransactionRequest = ProcessTransactionRequest(
          amount = request.amount,
          idempotencyKey = request.idempotencyKey,
          originatorWalletId = wallet.id,
          originatorSubwalletType = SubwalletType.RealMoney,
          transactionType = TransactionType.Hold
        )

        val transaction = transactionsService.create(createTransactionRequest)

        for {
          processTransactionTuple <- transactionsService.process(transaction).left.map { e =>
            transactionsService.updateStatus(transaction.id, TransactionStatus.Failed)
            InvestmentFailedError(e.message)
          }
          executedTransaction <- transactionsService.execute(processTransactionTuple).left.map(e => InvestmentFailedError(e.message))
        } yield executedTransaction
    }
  }
}
