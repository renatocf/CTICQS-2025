package digitalwallet.data.models

import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.TransactionsRepo
import java.math.BigDecimal
import java.time.LocalDateTime

abstract class Transaction(
    val id: String,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val originatorWalletId: String,
    val originatorSubwalletType: SubwalletType,
    val insertedAt: LocalDateTime,
    val completedAt: LocalDateTime? = null,
    val failedAt: LocalDateTime? = null,
    val status: TransactionStatus,
    val statusReason: String? = null,
    val metadata: TransactionMetadata?,

    private val transactionsRepo: TransactionsRepo,
) {
    abstract fun validate()

    fun updateStatus(newStatus: TransactionStatus, statusReason: String? = null, at: LocalDateTime? = LocalDateTime.now()) {
        transactionsRepo.update(this.id, status = newStatus, statusReason = statusReason, at = at)
    }

    abstract fun postToLedger() : LocalDateTime
    abstract fun reversePostToLedger()
}

