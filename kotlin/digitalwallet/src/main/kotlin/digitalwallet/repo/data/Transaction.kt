package digitalwallet.repo.data

import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.data.enums.TransactionType
import digitalwallet.data.models.Deposit
import digitalwallet.data.models.Hold
import digitalwallet.data.models.Transfer
import digitalwallet.data.models.Withdraw
import digitalwallet.data.models.Transaction as TransactionDto
import java.math.BigDecimal
import java.time.LocalDateTime

data class Transaction(
    val id: String,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val originatorWalletId: String,
    val originatorSubwalletType: SubwalletType,
    val beneficiaryWalletId: String? = null,
    val beneficiarySubwalletType: SubwalletType? = null,
    val type: TransactionType,
    val insertedAt: LocalDateTime,
    var completedAt: LocalDateTime? = null,
    var failedAt: LocalDateTime? = null,
    var status: TransactionStatus,
    var statusReason: String? = null,
    val metadata: TransactionMetadata?
) {
    fun dto() : TransactionDto {
        return when(this.type) {
            TransactionType.DEPOSIT -> Deposit(
                id = this.id,
                amount = this.amount,
                idempotencyKey = this.idempotencyKey,
                originatorWalletId = this.originatorWalletId,
                originatorSubwalletType = this.originatorSubwalletType,
                insertedAt = this.insertedAt,
                failedAt = this.failedAt,
                status = this.status,
                statusReason = this.statusReason,
                metadata = this.metadata
            )
            TransactionType.WITHDRAW -> Withdraw(
                id = this.id,
                amount = this.amount,
                idempotencyKey = this.idempotencyKey,
                originatorWalletId = this.originatorWalletId,
                originatorSubwalletType = this.originatorSubwalletType,
                insertedAt = this.insertedAt,
                failedAt = this.failedAt,
                status = this.status,
                statusReason = this.statusReason,
                metadata = this.metadata
            )
            TransactionType.HOLD -> Hold(
                id = this.id,
                amount = this.amount,
                idempotencyKey = this.idempotencyKey,
                originatorWalletId = this.originatorWalletId,
                originatorSubwalletType = this.originatorSubwalletType,
                insertedAt = this.insertedAt,
                failedAt = this.failedAt,
                status = this.status,
                statusReason = this.statusReason,
                metadata = this.metadata
            )
            TransactionType.TRANSFER -> Transfer(
                id = this.id,
                amount = this.amount,
                idempotencyKey = this.idempotencyKey,
                originatorWalletId = this.originatorWalletId,
                originatorSubwalletType = this.originatorSubwalletType,
                beneficiaryWalletId = this.beneficiaryWalletId!!,
                beneficiarySubwalletType = this.beneficiarySubwalletType!!,
                insertedAt = this.insertedAt,
                failedAt = this.failedAt,
                status = this.status,
                statusReason = this.statusReason,
                metadata = this.metadata
            )
        }
    }
}