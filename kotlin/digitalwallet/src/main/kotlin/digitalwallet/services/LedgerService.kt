package digitalwallet.services

import digitalwallet.data.common.*
import digitalwallet.data.common.exceptions.DepositNotAllowed
import digitalwallet.data.common.exceptions.InsufficientFunds
import digitalwallet.data.common.exceptions.TransferNotAllowed
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.data.enums.TransactionType
import digitalwallet.data.models.Transaction
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDateTime

@Singleton
class LedgerService(
    val ledgerRepo: LedgerRepo,
) {
//    fun createJournalEntries(transaction: Transaction) {
//        var journalEntries = emptyList<CreateJournalEntry>()
//        when (transaction.type) {
//            TransactionType.DEPOSIT -> journalEntries = deposit(transaction)
//            TransactionType.WITHDRAW-> journalEntries = withdraw(transaction)
//            TransactionType.TRANSFER -> journalEntries = transfer(transaction)
//            TransactionType.HOLD -> journalEntries = hold(transaction)
//        }
//
//        val postedAt = LocalDateTime.now()
//        for (journalEntry in journalEntries) {
//            ledgerRepo.insertJournalEntry(journalEntry, postedAt = postedAt)
//        }
//
//        transaction.updateStatus(TransactionStatus.COMPLETED, completedAt = postedAt)
//    }

    fun postJournalEntries(journalEntries: List<CreateJournalEntry>) : LocalDateTime {
        val postedAt = LocalDateTime.now()

        for (journalEntry in journalEntries) {
            ledgerRepo.insertJournalEntry(journalEntry, postedAt = postedAt)
        }

        return postedAt
    }

    fun getBalance(walletId: String, balanceConfig: List<BalanceConfig>) : BigDecimal {
        return ledgerRepo.getBalance(walletId, balanceConfig)
    }

//    private fun deposit(transaction: Transaction) : List<CreateJournalEntry> {
//        return listOf(
//            CreateJournalEntry(
//                walletId = transaction.originatorWalletId,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.AVAILABLE,
//                amount = transaction.amount,
//            ),
//            CreateJournalEntry(
//                walletId = null,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.INTERNAL,
//                amount = -transaction.amount,
//            ),
//        )
//    }
//
//    private fun withdraw(transaction: Transaction) : List<CreateJournalEntry> {
//        return listOf(
//            CreateJournalEntry(
//                walletId = transaction.originatorWalletId,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.AVAILABLE,
//                amount = -transaction.amount,
//            ),
//            CreateJournalEntry(
//                walletId = null,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.INTERNAL,
//                amount = transaction.amount,
//            ),
//        )
//
//    }
//
//    private fun hold(transaction: Transaction) : List<CreateJournalEntry> {
//        return listOf(
//            CreateJournalEntry(
//                walletId = transaction.originatorWalletId,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.AVAILABLE,
//                amount = -transaction.amount,
//            ),
//            CreateJournalEntry(
//                walletId = transaction.originatorWalletId,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.HOLDING,
//                amount = transaction.amount,
//            ),
//        )
//    }
//
//    private fun transfer(transaction: Transaction) : List<CreateJournalEntry> {
//        val beneficiarySubwalletType = transaction.beneficiarySubwalletType ?: throw IllegalArgumentException("Invalid beneficiary subwallet type")
//
//        return listOf(
//            CreateJournalEntry(
//                walletId = transaction.originatorWalletId,
//                subwalletType = transaction.originatorSubwalletType,
//                balanceType = BalanceType.AVAILABLE,
//                amount = -transaction.amount,
//            ),
//            CreateJournalEntry(
//                walletId = transaction.beneficiaryWalletId,
//                subwalletType = beneficiarySubwalletType,
//                balanceType = BalanceType.AVAILABLE,
//                amount = transaction.amount,
//            ),
//        )
//    }
}