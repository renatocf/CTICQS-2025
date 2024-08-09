package digitalwallet.data.models

import digitalwallet.data.common.CreateJournalEntry
import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.TransactionsRepo
import digitalwallet.services.LedgerService
import digitalwallet.services.ValidationService
import java.math.BigDecimal
import java.time.LocalDateTime

class Deposit(
    id: String,
    amount: BigDecimal,
    idempotencyKey: String,
    originatorWalletId: String,
    originatorSubwalletType: SubwalletType,
    insertedAt: LocalDateTime,
    completedAt: LocalDateTime? = null,
    failedAt: LocalDateTime? = null,
    status: TransactionStatus,
    statusReason: String? = null,
    metadata: TransactionMetadata?,

    transactionsRepo: TransactionsRepo,

    private val validationService: ValidationService,
    private val ledgerService: LedgerService,
) : Transaction(
    id,
    amount,
    idempotencyKey,
    originatorWalletId,
    originatorSubwalletType,
    insertedAt,
    completedAt,
    failedAt,
    status,
    statusReason,
    metadata,
    transactionsRepo,
) {
    override fun validate() {
        validationService.validateExternalTransaction(this)
    }

    override fun postToLedger() : LocalDateTime {
        val journalEntries = listOf(
            CreateJournalEntry(
                walletId = this.originatorWalletId,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.AVAILABLE,
                amount = this.amount,
            ),
            CreateJournalEntry(
                walletId = null,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.INTERNAL,
                amount = -this.amount,
            ),
        )

        return ledgerService.postJournalEntries(journalEntries)
    }

    override fun reversePostToLedger() {
        val journalEntries = listOf(
            CreateJournalEntry(
                walletId = this.originatorWalletId,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.AVAILABLE,
                amount = -this.amount,
            ),
            CreateJournalEntry(
                walletId = null,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.INTERNAL,
                amount = this.amount,
            ),
        )

        ledgerService.postJournalEntries(journalEntries)
    }
}