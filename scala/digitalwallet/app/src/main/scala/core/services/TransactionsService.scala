package core.services

import core.domain.entities.Transaction
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.enums.{BalanceType, TransactionStatus, TransactionType}
import core.domain.model.{CreateJournalEntry, CreateTransactionRequest}
import core.errors.{ExecutionFailed, MissingBeneficiarySubwalletType, MissingBeneficiaryWalletId, PartnerError, TransactionError}
import ports.{TransactionDatabase, TransactionFilter}

class TransactionsService(
  transactionsRepo: TransactionDatabase,
  validationService: TransactionValidationService,
  partnerService: PartnerService,
  ledgerService: LedgerService,
) {
  private type Action = Transaction => Either[PartnerError, Unit]
  private type ProcessTransactionTuple = (Transaction, List[CreateJournalEntry], TransactionStatus, Option[Action])

  def create(request: CreateTransactionRequest): Transaction = {
    transactionsRepo.insert(request)
  }

  def process(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    for {
      _ <- validationService.validateTransaction(transaction)
      processTransactionTuple <- processTransaction(transaction)
    } yield {
      processTransactionTuple
    }
  }

  def execute(tuple: ProcessTransactionTuple): Either[TransactionError, Transaction] = {
    val (transaction, journalEntries, statusOnSuccess, maybeExecuteAction) = tuple

    val actionResult: Either[PartnerError, Unit] = maybeExecuteAction match {
      case Some(action) => action(transaction)
      case None => Right(())
    }

    val updateResult = actionResult match {
      case Left(_) => transactionsRepo.update(transaction.id, TransactionStatus.TransientError)
      case Right(_) =>
        ledgerService.postJournalEntries(journalEntries)
        transactionsRepo.update(transaction.id, statusOnSuccess)
    }

    updateResult.left.map(e => ExecutionFailed(e.message))
  }

  def updateStatus(transactionId: String, status: TransactionStatus): Unit = {
    transactionsRepo.update(transactionId, status)
  }

  def failBatch(batchId: String): Unit = {
    transactionsRepo
      .find(TransactionFilter(
        batchId = Some(batchId)
      ))
      .foreach(transaction => {
        updateStatus(transaction.id, TransactionStatus.Failed)
      })
  }

  private def processTransaction(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    transaction.transactionType match {
      case TransactionType.Deposit => processDeposit(transaction)
      case TransactionType.Withdraw => processWithdraw(transaction)
      case TransactionType.Hold => processHold(transaction)
      case TransactionType.Transfer => processTransfer(transaction)
      case TransactionType.TransferFromHold => processTransferFromHold(transaction)
    }
  }

  private def processDeposit(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some(transaction.originatorWalletId),
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Available,
        amount = transaction.amount
      ),
      CreateJournalEntry(
        walletId = None,
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Internal,
        amount = -transaction.amount
      )
    )

    Right((transaction,journalEntries, TransactionStatus.Completed, None))
  }

  private def processWithdraw(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some(transaction.originatorWalletId),
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Available,
        amount = -transaction.amount
      ),
      CreateJournalEntry(
        walletId = None,
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Internal,
        amount = transaction.amount
      )
    )

    Right((transaction, journalEntries, TransactionStatus.Completed, None))
  }

  private def processHold(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some(transaction.originatorWalletId),
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Available,
        amount = -transaction.amount
      ),
      CreateJournalEntry(
        walletId = Some(transaction.originatorWalletId),
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Holding,
        amount = transaction.amount
      )
    )

    Right((transaction, journalEntries, TransactionStatus.Processing, None))
  }

  private def processTransfer(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    for {
      beneficiaryWalletId <- transaction.beneficiaryWalletId.toRight(MissingBeneficiaryWalletId(s"Transfer ${transaction.id} must contain beneficiaryWalletId"))
      beneficiarySubwalletType <- transaction.beneficiarySubwalletType.toRight(MissingBeneficiarySubwalletType(s"Wallet ${transaction.beneficiaryWalletId} not found"))
    } yield {
      val journalEntries = List(
        CreateJournalEntry(
          walletId = Some(transaction.originatorWalletId),
          subwalletType = transaction.originatorSubwalletType,
          balanceType = BalanceType.Available,
          amount = -transaction.amount
        ),
        CreateJournalEntry(
          walletId = Some(beneficiaryWalletId),
          subwalletType = beneficiarySubwalletType,
          balanceType = BalanceType.Available,
          amount = transaction.amount
        )
      )

      val partnerAction: Action = partnerService.executeInternalTransfer
      (transaction, journalEntries, TransactionStatus.Completed, Some(partnerAction))
    }
  }

  private def processTransferFromHold(transaction: Transaction): Either[TransactionError, ProcessTransactionTuple] = {
    for {
      beneficiaryWalletId <- transaction.beneficiaryWalletId.toRight(MissingBeneficiaryWalletId(s"TransferFromHold ${transaction.id} must contain beneficiaryWalletId"))
      beneficiarySubwalletType <- transaction.beneficiarySubwalletType.toRight(MissingBeneficiarySubwalletType(s"Wallet ${transaction.beneficiaryWalletId} not found"))
    } yield {
      val journalEntries = List(
        CreateJournalEntry(
          walletId = Some(transaction.originatorWalletId),
          subwalletType = transaction.originatorSubwalletType,
          balanceType = BalanceType.Holding,
          amount = -transaction.amount
        ),
        CreateJournalEntry(
          walletId = Some(beneficiaryWalletId),
          subwalletType = beneficiarySubwalletType,
          balanceType = BalanceType.Available,
          amount = transaction.amount
        )
      )

      val partnerAction: Action = partnerService.executeInternalTransfer
      (transaction, journalEntries, TransactionStatus.Completed, Some(partnerAction))
    }
  }

  def releaseHold(transaction: Transaction): Unit = {
    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some(transaction.originatorWalletId),
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Available,
        amount = transaction.amount
      ),
      CreateJournalEntry(
        walletId = Some(transaction.originatorWalletId),
        subwalletType = transaction.originatorSubwalletType,
        balanceType = BalanceType.Holding,
        amount = -transaction.amount
      )
    )

    ledgerService.postJournalEntries(journalEntries)
  }
}
