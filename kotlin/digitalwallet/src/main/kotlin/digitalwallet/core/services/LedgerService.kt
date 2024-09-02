package digitalwallet.core.services

import digitalwallet.core.domain.models.LedgerQuery
import digitalwallet.core.domain.models.CreateJournalEntry
import digitalwallet.ports.LedgerDatabase
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDateTime

@Singleton
class LedgerService(
    private val ledgerRepo: LedgerDatabase,
) {
    fun postJournalEntries(journalEntries: List<CreateJournalEntry>) : LocalDateTime {
        val postedAt = LocalDateTime.now()

        for (journalEntry in journalEntries) {
            ledgerRepo.insertJournalEntry(journalEntry, postedAt = postedAt)
        }

        return postedAt
    }

    fun getBalance(walletId: String, ledgerQuery: List<LedgerQuery>) : BigDecimal {
        return ledgerRepo.getBalance(walletId, ledgerQuery)
    }
}