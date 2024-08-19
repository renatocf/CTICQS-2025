package digitalwallet.repo

import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.StatusTransitionNotAllowed
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.data.Transaction
import digitalwallet.data.models.Transaction as TransactionModel
import java.time.LocalDateTime
import java.util.UUID

data class TransactionFilter(
    val id: String? = null,
    val batchId: String? = null,
    val status: TransactionStatus? = null,
    val subwalletType: List<SubwalletType>? = null,
)

class TransactionsRepo {
    private val transactions = mutableMapOf<String, Transaction>()

    fun insert(request: ProcessTransactionRequest) : TransactionModel {
        val id = UUID.randomUUID().toString()

        val transaction = Transaction(
            id = id,
            amount = request.amount,
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

    fun find(filter: TransactionFilter) : List<TransactionModel> {
        return transactions.values.filter { transaction ->
            (filter.id?.let { it == transaction.id } ?: true) &&
                    (filter.batchId?.let { it == transaction.batchId } ?: true) &&
                    (filter.status?.let { it == transaction.status } ?: true) &&
                    (filter.subwalletType?.let { transaction.originatorSubwalletType in it} ?: true)
        }.map { it.toModel() }
    }

    private fun validateTransition(status: TransactionStatus, newStatus: TransactionStatus) {
        val allowedStatus : List<TransactionStatus>

        when (status) {
            TransactionStatus.CREATING -> {
                allowedStatus = listOf(TransactionStatus.CREATING, TransactionStatus.PROCESSING, TransactionStatus.TRANSIENT_ERROR)
            }
            TransactionStatus.PROCESSING -> {
                allowedStatus = listOf(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED, TransactionStatus.FAILED, TransactionStatus.TRANSIENT_ERROR)
            }
            TransactionStatus.COMPLETED -> {
                allowedStatus = listOf(TransactionStatus.COMPLETED)
            }
            TransactionStatus.FAILED -> {
                allowedStatus = listOf(TransactionStatus.FAILED)
            }
            TransactionStatus.TRANSIENT_ERROR -> {
                allowedStatus = listOf(TransactionStatus.TRANSIENT_ERROR)
            }
        }

        if (newStatus !in allowedStatus) {
            throw StatusTransitionNotAllowed("Attempt to transition transaction from $status to $newStatus")
        }
    }

    fun update(transactionId: String, status: TransactionStatus, statusReason : String? = null, at : LocalDateTime? = LocalDateTime.now()) {
        val transaction = transactions[transactionId]!!

        validateTransition(status = transaction.status, newStatus = status)

        if (status == TransactionStatus.FAILED) {
            transaction.failedAt = at
        }

        if (status == TransactionStatus.COMPLETED) {
            transaction.completedAt = at
        }

        transaction.status = status
        transaction.statusReason = statusReason
    }
}