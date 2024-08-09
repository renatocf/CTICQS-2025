package digitalwallet.repo

import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.StatusTransitionNotAllowed
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.data.Transaction
import java.time.LocalDateTime
import java.util.UUID

class TransactionsRepo {
    private val transactions = mutableMapOf<String, Transaction>()

    fun insert(request: ProcessTransactionRequest) : Transaction {
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
        return transaction
    }

    private fun validateTransition(status: TransactionStatus, newStatus: TransactionStatus) {
        val allowedStatus : List<TransactionStatus>

        when (status) {
            TransactionStatus.CREATING -> {
                allowedStatus = listOf(TransactionStatus.CREATING, TransactionStatus.PROCESSING)
            }
            TransactionStatus.PROCESSING -> {
                allowedStatus = listOf(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED, TransactionStatus.FAILED)
            }
            TransactionStatus.COMPLETED -> {
                allowedStatus = listOf(TransactionStatus.COMPLETED)
            }
            TransactionStatus.FAILED -> {
                allowedStatus = listOf(TransactionStatus.FAILED)
            }
        }

        if (newStatus !in allowedStatus) {
            throw StatusTransitionNotAllowed("Attempt to transition transaction from $status to $newStatus")
        }
    }

    fun update(transactionId: String, status: TransactionStatus, statusReason : String? = null, at : LocalDateTime? = null) {
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