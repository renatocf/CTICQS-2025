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
import digitalwallet.services.PartnerService
import digitalwallet.services.ValidationService
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
    statusReason: String? = null,
    metadata: TransactionMetadata?,
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
    statusReason,
    metadata,
) {
    override fun validate(walletsRepo: WalletsRepo, ledgerService: LedgerService) {
        if (this.originatorSubwalletType !in listOf(SubwalletType.REAL_MONEY, SubwalletType.INVESTMENT)) {
            throw HoldNotAllowed("Hold not allowed on ${this.originatorSubwalletType} type")
        }

        validateBalance(walletsRepo, ledgerService)
    }

    override suspend fun process(transactionsRepo: TransactionsRepo, ledgerService: LedgerService, partnerService: PartnerService) {
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

        val postedAt = ledgerService.postJournalEntries(journalEntries)

        this.updateStatus(transactionsRepo, TransactionStatus.PROCESSING, at = postedAt)
    }

    override fun reverseJournalEntries(ledgerService: LedgerService) : LocalDateTime {
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

        return ledgerService.postJournalEntries(journalEntries)
    }
}