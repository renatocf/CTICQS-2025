package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.PartnerService
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import java.math.BigDecimal
import java.time.LocalDateTime

class Withdraw(
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
        validateExternalTransaction()
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
                    walletId = null,
                    subwalletType = this.originatorSubwalletType,
                    balanceType = BalanceType.INTERNAL,
                    amount = this.amount,
                ),
            )

        val postedAt = ledgerService.postJournalEntries(journalEntries)

        this.updateStatus(transactionsRepo, newStatus = TransactionStatus.COMPLETED, at = postedAt)
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
                    walletId = null,
                    subwalletType = this.originatorSubwalletType,
                    balanceType = BalanceType.INTERNAL,
                    amount = -this.amount,
                ),
            )

        return ledgerService.postJournalEntries(journalEntries)
    }
}
