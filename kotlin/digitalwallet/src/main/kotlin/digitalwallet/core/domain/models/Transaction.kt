package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.exceptions.ExternalTransactionValidationException
import digitalwallet.core.exceptions.InsufficientFundsException
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.PartnerService
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import java.math.BigDecimal
import java.time.LocalDateTime

abstract class Transaction(
    val id: String,
    val batchId: String? = null,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val originatorWalletId: String,
    val originatorSubwalletType: SubwalletType,
    val insertedAt: LocalDateTime,
    var reversedAt: LocalDateTime? = null,
    var completedAt: LocalDateTime? = null,
    var failedAt: LocalDateTime? = null,
    var status: TransactionStatus,
    var statusReason: String? = null,
    val metadata: TransactionMetadata? = null,
) {
    abstract fun validate(
        walletsRepo: WalletsDatabase,
        ledgerService: LedgerService,
    )

    fun validateExternalTransaction() {
        if (this.originatorSubwalletType != SubwalletType.REAL_MONEY) {
            throw ExternalTransactionValidationException("External transaction not allowed on ${this.originatorSubwalletType} type")
        }
    }

    fun validateBalance(
        walletsRepo: WalletsDatabase,
        ledgerService: LedgerService,
    ) {
        val walletId = this.originatorWalletId
        val wallet = walletsRepo.findById(walletId) ?: throw NoSuchElementException("Wallet $walletId not found")

        val balance = wallet.getAvailableBalance(ledgerService)

        if (this.amount > balance) {
            throw InsufficientFundsException("Wallet has no sufficient funds")
        }
    }

    fun updateStatus(
        transactionsRepo: TransactionsDatabase,
        newStatus: TransactionStatus,
        statusReason: String? = null,
        at: LocalDateTime? = LocalDateTime.now(),
    ) {
        transactionsRepo.update(this.id, status = newStatus, statusReason = statusReason, at = at)

        if (newStatus == TransactionStatus.FAILED) {
            this.failedAt = at
        }

        if (newStatus == TransactionStatus.COMPLETED) {
            this.completedAt = at
        }

        this.status = newStatus
        this.statusReason = statusReason
    }

    abstract suspend fun process(
        transactionsRepo: TransactionsDatabase,
        ledgerService: LedgerService,
        partnerService: PartnerService,
    )

    fun reverse(ledgerService: LedgerService) {
        if (this.reversedAt == null) {
            val postedAt = reverseJournalEntries(ledgerService)
            this.reversedAt = postedAt
        }
    }

    abstract fun reverseJournalEntries(ledgerService: LedgerService): LocalDateTime
}
