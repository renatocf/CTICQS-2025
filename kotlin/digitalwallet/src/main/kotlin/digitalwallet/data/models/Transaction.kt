package digitalwallet.data.models

import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.TransactionsRepo
import digitalwallet.repo.WalletsRepo
import digitalwallet.services.ExternalTransactionValidationException
import digitalwallet.services.InsufficientFundsException
import digitalwallet.services.LedgerService
import digitalwallet.services.PartnerService
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
    val metadata: TransactionMetadata?,
) {
    abstract fun validate(walletsRepo: WalletsRepo, ledgerService: LedgerService)

    fun validateExternalTransaction() {
        if (this.originatorSubwalletType != SubwalletType.REAL_MONEY) {
            throw ExternalTransactionValidationException("External transaction not allowed on ${this.originatorSubwalletType} type")
        }
    }

    fun validateBalance(walletsRepo: WalletsRepo, ledgerService: LedgerService) {
        val walletId = this.originatorWalletId
        val wallet = walletsRepo.findById(walletId)?.dto() ?: throw NoSuchElementException("Wallet $walletId not found")

        val balance = wallet.getAvailableBalance(ledgerService)

        if (this.amount > balance) {
            throw InsufficientFundsException("Wallet has no sufficient funds ")
        }
    }

    fun updateStatus(transactionsRepo: TransactionsRepo, newStatus: TransactionStatus, statusReason: String? = null, at: LocalDateTime? = LocalDateTime.now()) {
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

    abstract suspend fun process(transactionsRepo: TransactionsRepo, ledgerService: LedgerService, partnerService: PartnerService)

    fun reverse(ledgerService: LedgerService) {
        if (this.reversedAt == null) {
            val postedAt = reverseJournalEntries(ledgerService)
            this.reversedAt = postedAt
        }
    }

    abstract fun reverseJournalEntries(ledgerService: LedgerService) : LocalDateTime
}

