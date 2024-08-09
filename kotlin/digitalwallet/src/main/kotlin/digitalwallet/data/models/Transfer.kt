package digitalwallet.data.models

import digitalwallet.data.common.CreateJournalEntry
import digitalwallet.data.common.TransactionMetadata
import digitalwallet.data.common.exceptions.TransferNotAllowed
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

class Transfer(
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

    val beneficiaryWalletId: String,
    val beneficiarySubwalletType: SubwalletType,

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
        val originatorSubwalletType = this.originatorSubwalletType
        val beneficiarySubwalletType = this.beneficiarySubwalletType

        val validTransferPairs = setOf(
            SubwalletType.REAL_MONEY to SubwalletType.EMERGENCY_FUND,
            SubwalletType.EMERGENCY_FUND to SubwalletType.REAL_MONEY
        )

        if ((originatorSubwalletType to beneficiarySubwalletType) !in validTransferPairs) {
            throw TransferNotAllowed("Transfer not allowed between $originatorSubwalletType and $beneficiarySubwalletType types")
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
                walletId = this.beneficiaryWalletId,
                subwalletType = this.beneficiarySubwalletType,
                balanceType = BalanceType.AVAILABLE,
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
                walletId = this.beneficiaryWalletId,
                subwalletType = this.beneficiarySubwalletType,
                balanceType = BalanceType.AVAILABLE,
                amount = -this.amount,
            ),
        )

        ledgerService.postJournalEntries(journalEntries)
    }
}