package digitalwallet.repo

import digitalwallet.data.common.BalanceConfig
import digitalwallet.data.common.CreateJournalEntry
import digitalwallet.repo.data.JournalEntry
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class LedgerRepo {
    private val journalEntries = mutableMapOf<String, JournalEntry>()

    fun insertJournalEntry(createJournalEntry: CreateJournalEntry, postedAt: LocalDateTime? = null) {
        val id = UUID.randomUUID().toString()

        val journalEntry = JournalEntry(
            id = id,
            walletId = createJournalEntry.walletId,
            amount = createJournalEntry.amount,
            subwalletType = createJournalEntry.subwalletType,
            balanceType = createJournalEntry.balanceType,
        )

        journalEntries[id] = journalEntry
    }

    fun getBalance(walletId: String, balanceConfigs: List<BalanceConfig>) : BigDecimal {
        return balanceConfigs.flatMap { bc -> journalEntries.values.filter {
            it.walletId == walletId && it.subwalletType == bc.subwalletType && it.balanceType == bc.balanceType
        } }.sumOf { it.amount }
    }
}