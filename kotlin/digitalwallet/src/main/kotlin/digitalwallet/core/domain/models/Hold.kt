package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.exceptions.HoldNotAllowed
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.PartnerService
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import java.math.BigDecimal
import java.time.LocalDateTime

class Hold(
    id: String,
    batchId: String? = null,
    amount: BigDecimal,
    idempotencyKey: String,
    originatorWalletId: String,
    originatorSubwalletType: SubwalletType,
    insertedAt: LocalDateTime,
    reversedAt: LocalDateTime? = null,
    completedAt: LocalDateTime? = null,
    failedAt: LocalDateTime? = null,
    status: TransactionStatus,
) : Transaction(
        id,
        batchId,
        amount,
        idempotencyKey,
        originatorWalletId,
        originatorSubwalletType,
        insertedAt,
        reversedAt,
        completedAt,
        failedAt,
        status,
    ) {
    override fun validate(
        walletsRepo: WalletsDatabase,
        ledgerService: LedgerService,
    ) {
        if (this.originatorSubwalletType !in listOf(SubwalletType.REAL_MONEY, SubwalletType.INVESTMENT)) {
            throw HoldNotAllowed("Hold not allowed on ${this.originatorSubwalletType} type")
        }

        validateBalance(walletsRepo, ledgerService)
    }

    override suspend fun process(
        transactionsRepo: TransactionsDatabase,
        ledgerService: LedgerService,
        partnerService: PartnerService,
    ) {
        val journalEntries =
            listOf(
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

        val postedAt = ledgerService.postJournalEntries(journalEntries)

        this.updateStatus(transactionsRepo, TransactionStatus.PROCESSING, at = postedAt)
    }

    override fun reverseJournalEntries(ledgerService: LedgerService): LocalDateTime {
        val journalEntries =
            listOf(
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

        return ledgerService.postJournalEntries(journalEntries)
    }
}
