package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.exceptions.TransferNotAllowed
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.PartnerService
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import java.math.BigDecimal
import java.time.LocalDateTime

class Transfer(
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
    metadata: TransactionMetadata? = null,
    val beneficiaryWalletId: String,
    val beneficiarySubwalletType: SubwalletType,
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
        metadata,
    ) {
    override fun validate(
        walletsRepo: WalletsDatabase,
        ledgerService: LedgerService,
    ) {
        val originatorSubwalletType = this.originatorSubwalletType
        val beneficiarySubwalletType = this.beneficiarySubwalletType

        val validTransferPairs =
            setOf(
                SubwalletType.REAL_MONEY to SubwalletType.EMERGENCY_FUND,
                SubwalletType.EMERGENCY_FUND to SubwalletType.REAL_MONEY,
            )

        if ((originatorSubwalletType to beneficiarySubwalletType) !in validTransferPairs) {
            throw TransferNotAllowed("Transfer not allowed between $originatorSubwalletType and $beneficiarySubwalletType types")
        }

        validateBalance(walletsRepo, ledgerService)
    }

    override suspend fun process(
        transactionsRepo: TransactionsDatabase,
        ledgerService: LedgerService,
        partnerService: PartnerService,
    ) {
        partnerService.executeInternalTransfer(this)

        val journalEntries =
            listOf(
                CreateJournalEntry(
                    walletId = this.originatorWalletId,
                    subwalletType = this.originatorSubwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = -this.amount,
                ),
                CreateJournalEntry(
                    walletId = this.beneficiaryWalletId,
                    subwalletType = this.beneficiarySubwalletType,
                    balanceType = BalanceType.AVAILABLE,
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
                    walletId = this.beneficiaryWalletId,
                    subwalletType = this.beneficiarySubwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = -this.amount,
                ),
            )

        return ledgerService.postJournalEntries(journalEntries)
    }
}
