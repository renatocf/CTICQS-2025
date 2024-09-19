package adapters

import core.domain.entities.Transaction
import core.domain.enums.TransactionStatus
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.model.ProcessTransactionRequest
import core.errors.{TransactionDbError, TransactionNotFound}
import ports.{TransactionDatabase, TransactionFilter}

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.mutable

class TransactionInMemoryDatabase extends TransactionDatabase {
  private val transactions: mutable.Map[String, Transaction] = mutable.Map()

  override def insert(request: ProcessTransactionRequest): Transaction = {
    val id = UUID.randomUUID().toString

    val transaction = Transaction(
      id = id,
      amount = request.amount,
      batchId = request.batchId,
      idempotencyKey = request.idempotencyKey,
      originatorWalletId = request.originatorWalletId,
      originatorSubwalletType = request.originatorSubwalletType,
      beneficiaryWalletId = request.beneficiaryWalletId,
      beneficiarySubwalletType = request.beneficiarySubwalletType,
      transactionType = request.transactionType,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing,
      metadata = request.metadata
    )

    transactions(id) = transaction
    transaction
  }

  override def find(filter: TransactionFilter): List[Transaction] = {
    transactions.values
      .filter { transaction =>
        (filter.id.forall(_ == transaction.id)) &&
          (filter.batchId.forall(transaction.batchId.contains(_))) &&
          (filter.status.forall(_ == transaction.status)) &&
          (filter.subwalletType.forall(types => types.contains(transaction.originatorSubwalletType)))
      }
      .toList
  }

  override def update(transactionId: String, status: TransactionStatus, statusReason: Option[String], at: Option[LocalDateTime]): Either[TransactionDbError, Unit] = {
    for {
      transaction <- transactions.get(transactionId).toRight(TransactionNotFound(s"Transaction not found: $transactionId"))
      _ <- validateTransition(transaction.status, status)
    } yield {
      status match {
        case TransactionStatus.Failed => transaction.failedAt = at
        case TransactionStatus.Completed => transaction.completedAt = at
        case _ =>
      }

      transaction.status = status
      transaction.statusReason = statusReason
    }
  }

  def clear(): Unit = {
    transactions.clear()
  }
}
