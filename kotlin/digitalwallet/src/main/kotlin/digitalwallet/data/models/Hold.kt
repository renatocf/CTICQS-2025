package digitalwallet.data.models

import digitalwallet.data.common.CreateJournalEntry
import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.common.exceptions.HoldNotAllowed
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.TransactionsRepo
import digitalwallet.repo.WalletsRepo
import digitalwallet.services.InsufficientFundsException
import digitalwallet.services.LedgerService
import digitalwallet.services.ValidationService
import java.math.BigDecimal
import java.time.LocalDateTime

class Hold(
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
    transactionsRepo
) {
    override fun validate() {
        if (this.originatorSubwalletType !in listOf(SubwalletType.REAL_MONEY, SubwalletType.INVESTMENT)) {
            throw HoldNotAllowed("Hold not allowed on ${this.originatorSubwalletType} type")
        }

        validationService.validateBalance(this)
    }

    override fun postToLedger() : LocalDateTime {
        val journalEntries = listOf(
            CreateJournalEntry(
                walletId = this.originatorWalletId,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.AVAILABLE,
                amount = -this.amount,
            ),
            CreateJournalEntry(
                walletId = this.originatorWalletId,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.HOLDING,
                amount = this.amount,
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
                amount = this.amount,
            ),
            CreateJournalEntry(
                walletId = this.originatorWalletId,
                subwalletType = this.originatorSubwalletType,
                balanceType = BalanceType.HOLDING,
                amount = -this.amount,
            ),
        )

        ledgerService.postJournalEntries(journalEntries)
    }
}