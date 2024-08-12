package digitalwallet.data.models

import digitalwallet.data.common.CreateJournalEntry
import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.common.exceptions.TransferNotAllowed
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.TransactionsRepo
import digitalwallet.repo.WalletsRepo
import digitalwallet.services.LedgerService
import digitalwallet.services.PartnerService
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
    statusReason: String? = null,
    metadata: TransactionMetadata?,

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
    statusReason,
    metadata,
) {
    override fun validate(walletsRepo: WalletsRepo, ledgerService: LedgerService) {
        val originatorSubwalletType = this.originatorSubwalletType
        val beneficiarySubwalletType = this.beneficiarySubwalletType

        val validTransferPairs = setOf(
            SubwalletType.REAL_MONEY to SubwalletType.EMERGENCY_FUND,
            SubwalletType.EMERGENCY_FUND to SubwalletType.REAL_MONEY
        )

        if ((originatorSubwalletType to beneficiarySubwalletType) !in validTransferPairs) {
            throw TransferNotAllowed("Transfer not allowed between $originatorSubwalletType and $beneficiarySubwalletType types")
        }

        validateBalance(walletsRepo, ledgerService)
    }

    override suspend fun process(transactionsRepo: TransactionsRepo, ledgerService: LedgerService, partnerService: PartnerService) {
        partnerService.executeInternalTransfer(this)

        val journalEntries = listOf(
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

    override fun reverseJournalEntries(ledgerService: LedgerService) : LocalDateTime {
        val journalEntries = listOf(
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