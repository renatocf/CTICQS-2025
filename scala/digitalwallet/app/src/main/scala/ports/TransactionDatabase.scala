package ports

import core.domain.entities.Transaction
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.TransactionStatus
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.model.ProcessTransactionRequest
import core.errors.{StatusTransitionNotAllowed, TransactionDbError}

import java.time.LocalDateTime

case class TransactionFilter(
  id: Option[String] = None,
  batchId: Option[String] = None,
  status: Option[TransactionStatus] = None,
  subwalletType: Option[List[SubwalletType]] = None,
)

trait TransactionDatabase {
  def insert(request: ProcessTransactionRequest): Transaction
  def find(filter: TransactionFilter): List[Transaction]

  def update(
              transactionId: String,
              status: TransactionStatus,
              statusReason: Option[String] = None,
              at: Option[LocalDateTime] = Some(LocalDateTime.now())
            ): Either[TransactionDbError, Unit]

  def validateTransition(status: TransactionStatus, newStatus: TransactionStatus): Either[StatusTransitionNotAllowed, Unit] = {
    val allowedStatus: List[TransactionStatus] = status match {
      case TransactionStatus.Creating => List(TransactionStatus.Creating, TransactionStatus.Processing, TransactionStatus.TransientError)
      case TransactionStatus.Processing => List(TransactionStatus.Processing, TransactionStatus.Completed, TransactionStatus.Failed, TransactionStatus.TransientError)
      case TransactionStatus.Completed => List(TransactionStatus.Completed)
      case TransactionStatus.Failed => List(TransactionStatus.Failed)
      case TransactionStatus.TransientError => List(TransactionStatus.TransientError)
    }

    if (allowedStatus.contains(newStatus)) Right(())
    else Left(StatusTransitionNotAllowed(s"Attempt to transition transaction from $status to $newStatus"))
  }
}
