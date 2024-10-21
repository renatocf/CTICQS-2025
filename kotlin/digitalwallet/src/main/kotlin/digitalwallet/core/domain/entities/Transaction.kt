package digitalwallet.core.domain.entities

import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.models.*
import java.math.BigDecimal
import java.time.LocalDateTime
import digitalwallet.core.domain.models.Transaction as TransactionModel

data class Transaction(
    val id: String,
    val batchId: String? = null,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val originatorWalletId: String,
    val originatorSubwalletType: SubwalletType,
    val beneficiaryWalletId: String? = null,
    val beneficiarySubwalletType: SubwalletType? = null,
    val type: digitalwallet.core.domain.enums.TransactionType,
    val insertedAt: LocalDateTime,
    var completedAt: LocalDateTime? = null,
    var failedAt: LocalDateTime? = null,
    var status: digitalwallet.core.domain.enums.TransactionStatus,
    var statusReason: String? = null,
) {
    fun toModel(): TransactionModel =
        when (this.type) {
            digitalwallet.core.domain.enums.TransactionType.DEPOSIT ->
                Deposit(
                    id = this.id,
                    batchId = this.batchId,
                    amount = this.amount,
                    idempotencyKey = this.idempotencyKey,
                    originatorWalletId = this.originatorWalletId,
                    originatorSubwalletType = this.originatorSubwalletType,
                    insertedAt = this.insertedAt,
                    failedAt = this.failedAt,
                    status = this.status,
                )
            digitalwallet.core.domain.enums.TransactionType.WITHDRAW ->
                Withdraw(
                    id = this.id,
                    batchId = this.batchId,
                    amount = this.amount,
                    idempotencyKey = this.idempotencyKey,
                    originatorWalletId = this.originatorWalletId,
                    originatorSubwalletType = this.originatorSubwalletType,
                    insertedAt = this.insertedAt,
                    failedAt = this.failedAt,
                    status = this.status,
                )
            digitalwallet.core.domain.enums.TransactionType.HOLD ->
                Hold(
                    id = this.id,
                    batchId = this.batchId,
                    amount = this.amount,
                    idempotencyKey = this.idempotencyKey,
                    originatorWalletId = this.originatorWalletId,
                    originatorSubwalletType = this.originatorSubwalletType,
                    insertedAt = this.insertedAt,
                    failedAt = this.failedAt,
                    status = this.status,
                )
            digitalwallet.core.domain.enums.TransactionType.TRANSFER ->
                Transfer(
                    id = this.id,
                    batchId = this.batchId,
                    amount = this.amount,
                    idempotencyKey = this.idempotencyKey,
                    originatorWalletId = this.originatorWalletId,
                    originatorSubwalletType = this.originatorSubwalletType,
                    beneficiaryWalletId = this.beneficiaryWalletId!!,
                    beneficiarySubwalletType = this.beneficiarySubwalletType!!,
                    insertedAt = this.insertedAt,
                    failedAt = this.failedAt,
                    status = this.status,
                )
            digitalwallet.core.domain.enums.TransactionType.TRANSFER_FROM_HOLD ->
                TransferFromHold(
                    id = this.id,
                    batchId = this.batchId,
                    amount = this.amount,
                    idempotencyKey = this.idempotencyKey,
                    originatorWalletId = this.originatorWalletId,
                    originatorSubwalletType = this.originatorSubwalletType,
                    beneficiaryWalletId = this.beneficiaryWalletId!!,
                    beneficiarySubwalletType = this.beneficiarySubwalletType!!,
                    insertedAt = this.insertedAt,
                    failedAt = this.failedAt,
                    status = this.status,
                )
        }
}
