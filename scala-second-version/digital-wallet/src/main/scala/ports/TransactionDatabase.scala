package ports

import core.domain.entities.Transaction
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.TransactionStatus
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.model.CreateTransactionRequest
import core.errors.TransactionDbError

import java.time.LocalDateTime

case class TransactionFilter(
  id: Option[String] = None,
  batchId: Option[String] = None,
  status: Option[TransactionStatus] = None,
  subwalletType: Option[List[SubwalletType]] = None
)

trait TransactionDatabase {
  def insert(request: CreateTransactionRequest): Either[TransactionDbError, Transaction]

  def find(filter: TransactionFilter): List[Transaction]

  def update(
    transactionId: String,
    status: TransactionStatus,
    statusReason: Option[String] = None,
    at: Option[LocalDateTime] = Some(LocalDateTime.now())
  ): Either[TransactionDbError, Transaction]
}
