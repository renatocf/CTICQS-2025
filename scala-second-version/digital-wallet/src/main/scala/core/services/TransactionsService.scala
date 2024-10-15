package core.services

import core.domain.entities.Transaction
import core.domain.enums.TransactionStatus.{Processing, TransactionStatus}
import core.domain.enums.{BalanceType, TransactionStatus, TransactionType}
import core.domain.model.{CreateJournalEntry, CreateTransactionRequest}
import core.errors.{TransactionServiceError, *}
import ports.{TransactionDatabase, TransactionFilter}

type Action = Transaction => Either[PartnerServiceError, Unit]
type ProcessTransactionTuple = (Transaction, List[CreateJournalEntry], TransactionStatus, Option[Action])

class TransactionsService(
  transactionsRepo: TransactionDatabase,
  validationService: TransactionValidationService,
  partnerService: PartnerService,
  ledgerService: LedgerService,
) {

  def create(request: CreateTransactionRequest): Either[CreationError, Transaction] = {
    transactionsRepo
      .insert(request)
      .left
      .map(e => CreationError(e.message))
  }

  def process(transaction: Transaction): Either[ProcessError, ProcessTransactionTuple] = {
    for {
      _ <- validationService.validateTransaction(transaction).left.map(e => ProcessError(e.message))
      processTransactionTuple <- processTransaction(transaction).left.map(e => ProcessError(e.message))
    } yield {
      processTransactionTuple
    }
  }

  def execute(tuple: ProcessTransactionTuple): Either[TransactionServiceError, Transaction] = {
    val (transaction, journalEntries, statusOnSuccess, maybeExecuteAction) = tuple

    val actionResult: Either[PartnerServiceError, Unit] = maybeExecuteAction match {
      case Some(action) => action(transaction)
      case None => Right(())
    }

    val updateResult = actionResult match {
      case Left(_) => transactionsRepo.update(transaction.id, TransactionStatus.TransientError)
      case Right(_) =>
        ledgerService.postJournalEntries(journalEntries)
        transactionsRepo.update(transaction.id, statusOnSuccess)
    }

    updateResult.left.map(e => ExecutionError(e.message))
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

  private def processTransaction(transaction: Transaction): Either[TransactionServiceError, ProcessTransactionTuple] = {
    transaction.transactionType match {
      case TransactionType.Deposit => processDeposit(transaction)
      case TransactionType.Withdraw => processWithdraw(transaction)
      case TransactionType.Hold => processHold(transaction)
      case TransactionType.Transfer => processTransfer(transaction)
      case TransactionType.TransferFromHold => processTransferFromHold(transaction)
    }
  }

  private def processDeposit(transaction: Transaction): Either[TransactionServiceError, ProcessTransactionTuple] = {
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

  private def processWithdraw(transaction: Transaction): Either[TransactionServiceError, ProcessTransactionTuple] = {
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

  private def processHold(transaction: Transaction): Either[TransactionServiceError, ProcessTransactionTuple] = {
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

  private def processTransfer(transaction: Transaction): Either[TransactionServiceError, ProcessTransactionTuple] = {
    for {
      beneficiaryWalletId <- transaction.beneficiaryWalletId.toRight(ProcessError(s"Transfer ${transaction.id} must contain beneficiaryWalletId"))
      beneficiarySubwalletType <- transaction.beneficiarySubwalletType.toRight(ProcessError(s"Wallet ${transaction.beneficiaryWalletId} not found"))
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

  private def processTransferFromHold(transaction: Transaction): Either[TransactionServiceError, ProcessTransactionTuple] = {
    for {
      beneficiaryWalletId <- transaction.beneficiaryWalletId.toRight(ProcessError(s"TransferFromHold ${transaction.id} must contain beneficiaryWalletId"))
      beneficiarySubwalletType <- transaction.beneficiarySubwalletType.toRight(ProcessError(s"Wallet ${transaction.beneficiaryWalletId} not found"))
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
