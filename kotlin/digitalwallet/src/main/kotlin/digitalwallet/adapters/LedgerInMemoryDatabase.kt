package digitalwallet.adapters

import digitalwallet.core.domain.entities.JournalEntry
import digitalwallet.core.domain.models.CreateJournalEntry
import digitalwallet.core.domain.models.LedgerQuery
import digitalwallet.ports.LedgerDatabase
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Singleton
class LedgerInMemoryDatabase : LedgerDatabase {
    private val journalEntries = mutableMapOf<String, JournalEntry>()

    override fun insertJournalEntry(
        createJournalEntry: CreateJournalEntry,
        postedAt: LocalDateTime?,
    ) {
        val id = UUID.randomUUID().toString()

        val journalEntry =
            JournalEntry(
                id = id,
                walletId = createJournalEntry.walletId,
                amount = createJournalEntry.amount,
                subwalletType = createJournalEntry.subwalletType,
                balanceType = createJournalEntry.balanceType,
            )

        journalEntries[id] = journalEntry
    }

    override fun getBalance(
        walletId: String,
        ledgerQueries: List<LedgerQuery>,
    ): BigDecimal =
        ledgerQueries
            .flatMap { bc ->
                journalEntries.values.filter {
                    it.walletId == walletId && it.subwalletType == bc.subwalletType && it.balanceType == bc.balanceType
                }
            }.sumOf { it.amount }

    fun clear() {
        journalEntries.clear()
    }
}
