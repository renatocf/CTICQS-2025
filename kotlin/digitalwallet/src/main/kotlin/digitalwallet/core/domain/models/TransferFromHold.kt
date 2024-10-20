package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.exceptions.InsufficientFundsException
import digitalwallet.core.exceptions.TransferFromHoldNotAllowed
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.PartnerService
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import java.math.BigDecimal
import java.time.LocalDateTime

class TransferFromHold(
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
    private val beneficiaryWalletId: String,
    private val beneficiarySubwalletType: SubwalletType,
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
        val originatorWallet =
            walletsRepo.findById(this.originatorWalletId) ?: throw NoSuchElementException("Wallet ${this.originatorWalletId} not found")
        val beneficiaryWallet =
            walletsRepo.findById(this.beneficiaryWalletId) ?: throw NoSuchElementException("Wallet ${this.beneficiaryWalletId} not found")

        val validTransferFromHold =
            (originatorWallet is RealMoneyWallet && beneficiaryWallet is InvestmentWallet) ||
                (originatorWallet is InvestmentWallet && beneficiaryWallet is RealMoneyWallet)

        if (!validTransferFromHold) {
            throw TransferFromHoldNotAllowed("Transfer not allowed between wallets $originatorWalletId and $beneficiaryWalletId.")
        }

        val originatorSubwalletPendingBalance =
            ledgerService.getBalance(
                originatorWalletId,
                listOf(
                    LedgerQuery(
                        subwalletType = originatorSubwalletType,
                        balanceType = BalanceType.HOLDING,
                    ),
                ),
            )

        if (this.amount > originatorSubwalletPendingBalance) {
            throw InsufficientFundsException("Wallet has no sufficient funds")
        }
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
                    balanceType = BalanceType.HOLDING,
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
                    balanceType = BalanceType.HOLDING,
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
