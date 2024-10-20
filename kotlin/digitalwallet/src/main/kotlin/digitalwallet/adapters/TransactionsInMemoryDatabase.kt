package digitalwallet.adapters

import digitalwallet.core.domain.entities.Transaction
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.TransactionNotFound
import digitalwallet.ports.TransactionFilter
import digitalwallet.ports.TransactionsDatabase
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.UUID
import digitalwallet.core.domain.models.Transaction as TransactionModel

@Singleton
class TransactionsInMemoryDatabase : TransactionsDatabase {
    val transactions = mutableMapOf<String, Transaction>()

    override fun insert(request: ProcessTransactionRequest): TransactionModel {
        val id = UUID.randomUUID().toString()

        val transaction =
            Transaction(
                id = id,
                amount = request.amount,
                batchId = request.batchId,
                idempotencyKey = request.idempotencyKey,
                originatorWalletId = request.originatorWalletId,
                originatorSubwalletType = request.originatorSubwalletType,
                beneficiaryWalletId = request.beneficiaryWalletId,
                beneficiarySubwalletType = request.beneficiarySubwalletType,
                type = request.type,
                insertedAt = LocalDateTime.now(),
                status = TransactionStatus.PROCESSING,
                metadata = request.metadata,
            )

        transactions[id] = transaction
        return transaction.toModel()
    }

    override fun find(filter: TransactionFilter): List<TransactionModel> =
        transactions.values
            .filter { transaction ->
                (filter.id?.let { it == transaction.id } ?: true) &&
                    (filter.idempotencyKey?.let { it == transaction.idempotencyKey } ?: true) &&
                    (filter.batchId?.let { it == transaction.batchId } ?: true) &&
                    (filter.status?.let { it == transaction.status } ?: true) &&
                    (filter.subwalletType?.let { transaction.originatorSubwalletType in it } ?: true)
            }.map { it.toModel() }

    override fun update(
        transactionId: String,
        status: TransactionStatus,
        at: LocalDateTime?,
    ) {
        val transaction = transactions[transactionId] ?: throw TransactionNotFound("Transaction $transactionId not found.")

        if (status == TransactionStatus.FAILED) {
            transaction.failedAt = at
        }

        if (status == TransactionStatus.COMPLETED) {
            transaction.completedAt = at
        }

        transaction.status = status
    }

    fun clear() {
        transactions.clear()
    }
}
