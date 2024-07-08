package digitalwallet.services

import digitalwallet.data.common.*
import digitalwallet.data.common.exceptions.DepositNotAllowed
import digitalwallet.data.common.exceptions.HoldNotAllowed
import digitalwallet.data.common.exceptions.InsufficientFunds
import digitalwallet.data.common.exceptions.TransferNotAllowed
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

class LedgerService(
    ledgerRepo: LedgerRepo,
    subwalletRepo: SubwalletRepo,
) {
    fun postJournalEntries(journalEntries: Array<CreateJournalEntry>) {
        for (journalEntry in journalEntries) {
            ledgerRepo.insertJournalEntry(journalEntry)
        }
    }

    fun getBalance(walletId: String, balanceConfig: Array<BalanceConfig>) : BigDecimal {
        return ledgerRepo.getBalance(walletId, balanceConfig)
    }

    fun deposit(createDeposit: CreateDeposit) {
        val (walletId, subwalletType, amount) = createDeposit

        if (subwalletType != SubwalletType.REAL_MONEY) {
            throw DepositNotAllowed("Deposit not allowed for $walletId on $subwalletType type")
        }

        postJournalEntries(
            arrayOf(
                CreateJournalEntry(
                    walletId = walletId,
                    subwalletType = subwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = amount,
                ),
                CreateJournalEntry(
                    walletId = null,
                    subwalletType = subwalletType,
                    balanceType = BalanceType.INTERNAL,
                    amount = -amount,
                ),
            )
        )
    }

    fun withdraw(createWithdraw: CreateWithdraw) {
        val (walletId, subwalletType, amount) = createWithdraw

        if (subwalletType != SubwalletType.REAL_MONEY) {
            throw DepositNotAllowed("Withdraw not allowed for $walletId on $subwalletType type")
        }

        val balance = getBalance(walletId, BalanceConfig.availableRealMoney())

        if (amount > balance) {
            throw InsufficientFunds("Failed to withdraw $walletId on $subwalletType type due to insufficient funds")
        }

        postJournalEntries(
            arrayOf(
                CreateJournalEntry(
                    walletId = walletId,
                    subwalletType = subwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = -amount,
                ),
                CreateJournalEntry(
                    walletId = null,
                    subwalletType = subwalletType,
                    balanceType = BalanceType.INTERNAL,
                    amount = amount,
                ),
            )
        )
    }

    fun hold(createHold: CreateHold) {
        val (walletId, subwalletType, amount) = createHold

        val balanceConfig: Array<BalanceConfig>

        if (createHold.subwalletType == SubwalletType.REAL_MONEY) {
            balanceConfig = BalanceConfig.availableRealMoney()
        } else if (createHold.subwalletType == SubwalletType.INVESTMENT) {
            balanceConfig = BalanceConfig.availableInvestment()
        } else {
            throw HoldNotAllowed("Hold not allowed for ${createHold.walletId} on ${createHold.subwalletType} type")
        }

        val balance = getBalance(walletId, balanceConfig)

        if (amount > balance) {
            throw InsufficientFunds("Failed to hold $walletId on $subwalletType type due to insufficient funds")
        }

        postJournalEntries(
            arrayOf(
                CreateJournalEntry(
                    walletId = walletId,
                    subwalletType = subwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = -amount,
                ),
                CreateJournalEntry(
                    walletId = walletId,
                    subwalletType = subwalletType,
                    balanceType = BalanceType.HOLDING,
                    amount = amount,
                ),
            )
        )
    }

    fun transfer(createTransfer: CreateTransfer) {
        val (sourceWalletId, sourceSubwalletType, targetWalletId, targetSubwalletType, amount) = createTransfer

        if (!validateTransfer(sourceSubwalletType, targetSubwalletType)) {
            throw TransferNotAllowed("Transfer from $sourceSubwalletType on $sourceSubwalletType to $targetSubwalletType not allowed")
        }

        var balanceConfig: Array<BalanceConfig> = emptyArray()

        if (sourceSubwalletType == SubwalletType.REAL_MONEY) {
            balanceConfig = BalanceConfig.availableRealMoney()
        } else if (sourceSubwalletType == SubwalletType.INVESTMENT) {
            balanceConfig = BalanceConfig.availableInvestment()
        }

        val balance = getBalance(sourceWalletId, balanceConfig)

        if (amount > balance) {
            throw InsufficientFunds("Failed to transfer from $sourceSubwalletType on $sourceSubwalletType to $targetSubwalletType due to insufficient funds")
        }

        postJournalEntries(
            arrayOf(
                CreateJournalEntry(
                    walletId = sourceWalletId,
                    subwalletType = sourceSubwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = -amount,
                ),
                CreateJournalEntry(
                    walletId = targetWalletId,
                    subwalletType = targetSubwalletType,
                    balanceType = BalanceType.AVAILABLE,
                    amount = amount,
                ),
            )
        )
    }

    private fun validateTransfer(sourceSubwalletType: SubwalletType, targetSubwalletType: SubwalletType) : Boolean {
        return sourceSubwalletType == SubwalletType.REAL_MONEY && targetSubwalletType == SubwalletType.EMERGENCY_FUND ||
                sourceSubwalletType == SubwalletType.EMERGENCY_FUND && targetSubwalletType == SubwalletType.REAL_MONEY
    }
}