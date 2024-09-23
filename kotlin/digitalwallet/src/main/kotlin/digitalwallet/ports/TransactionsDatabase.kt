package digitalwallet.ports

import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.models.ProcessTransactionRequest
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
}