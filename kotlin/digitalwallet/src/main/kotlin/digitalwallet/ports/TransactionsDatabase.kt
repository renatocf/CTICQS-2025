package digitalwallet.ports

import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.StatusTransitionNotAllowed
import java.time.LocalDateTime
import digitalwallet.core.domain.models.Transaction as TransactionModel

data class TransactionFilter(
    val id: String? = null,
    val batchId: String? = null,
    val status: TransactionStatus? = null,
    val subwalletType: List<SubwalletType>? = null,
)

interface TransactionsDatabase {
    fun insert(request: ProcessTransactionRequest): TransactionModel
    fun find(filter: TransactionFilter): List<TransactionModel>
    fun update(
        transactionId: String,
        status: TransactionStatus,
        statusReason: String? = null,
        at: LocalDateTime? = LocalDateTime.now()
    )

    fun validateTransition(status: TransactionStatus, newStatus: TransactionStatus) {
        val allowedStatus: List<TransactionStatus>

        when (status) {
            TransactionStatus.CREATING -> {
                allowedStatus =
                    listOf(TransactionStatus.CREATING, TransactionStatus.PROCESSING, TransactionStatus.TRANSIENT_ERROR)
            }

            TransactionStatus.PROCESSING -> {
                allowedStatus = listOf(
                    TransactionStatus.PROCESSING,
                    TransactionStatus.COMPLETED,
                    TransactionStatus.FAILED,
                    TransactionStatus.TRANSIENT_ERROR
                )
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
}