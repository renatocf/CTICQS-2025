package core.services

import core.domain.entities.Transaction
import core.domain.enums.TransactionStatus.{Processing, TransactionStatus}
import core.domain.enums.{BalanceType, TransactionStatus, TransactionType}
import core.domain.model.{CreateJournalEntry, CreateTransactionRequest}
import core.errors.{TransactionServiceError, *}
import ports.{TransactionDatabase, TransactionFilter}
import core.utils as lib
import core.utils.Library
import cats.implicits.*

type Action = Transaction => Either[PartnerServiceError, Unit]
type ProcessTransactionTuple = (Transaction, List[CreateJournalEntry], TransactionStatus, Option[Action])

class TransactionsService(
  transactionsRepo: TransactionDatabase,
  validationService: TransactionValidationService,
  partnerService: PartnerService,
  ledgerService: LedgerService,
) {
  private val lib = Library()

  def create(request: CreateTransactionRequest): Either[CreationError, Transaction] = {
    transactionsRepo
      .insert(request)
      .left
      .map(e => CreationError(e.message))
  }

  def process(transaction: Transaction): Either[ProcessError, ProcessTransactionTuple] = {
    lib.maybeLogError(() => {
      for {
        _ <- validationService.validateTransaction(transaction).left.map(e => ProcessError(e.message))
        processTransactionTuple <- processTransaction(transaction).left.map(e => ProcessError(e.message))
      } yield processTransactionTuple
    })
  }

  def execute(tuple: ProcessTransactionTuple): Either[ExecutionError, Transaction] = {
    val (transaction, journalEntries, statusOnSuccess, maybeExecuteAction) = tuple

    val actionResult: Either[PartnerServiceError, Unit] = maybeExecuteAction match {
      case Some(action) => lib.maybeLogError(() => action(transaction))
      case None => Right(())
    }

    actionResult match {
      case Left(e) =>
        updateStatus(transaction.id, TransactionStatus.TransientError)
        Left(ExecutionError(e.message))
      case Right(_) =>
        ledgerService.postJournalEntries(journalEntries)
        Right(updateStatus(transaction.id, statusOnSuccess))
    }
  }

  def updateStatus(transactionId: String, status: TransactionStatus): Transaction = {
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

  def retryBatch(batchId: String): Either[TransactionServiceError, Unit] = {
    transactionsRepo
      .find(TransactionFilter(batchId = Some(batchId), status = Some(TransactionStatus.TransientError)))
      .traverse { t =>
        process(t).left.map { e =>
          // We are not really expecting a process error 
          // as we know it worked the first time
          ProcessError(e.message)
        }
      }
      .flatMap(tuples => {
        val failures =
          tuples
            .map { tuple => lib.retry(() => execute(tuple), 3) }
            .collect { case Left(error) => error }

        if (failures.nonEmpty) {
          Left(ExecutionError(s"Could not execute batch successfully."))
        } 
        else {
          for {
            originatingTransaction <- 
              transactionsRepo
                .find(TransactionFilter(idempotencyKey = Some(batchId)))
                .headOption
                .toRight(TransactionServiceInternalError(s"Could not find transaction with idempotency key ${batchId}"))
          } yield {
            updateStatus(originatingTransaction.id, TransactionStatus.Completed)
          }
        }
      })
  }
}
