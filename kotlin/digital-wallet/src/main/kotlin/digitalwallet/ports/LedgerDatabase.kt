package digitalwallet.ports

import digitalwallet.core.domain.models.CreateJournalEntry
import digitalwallet.core.domain.models.LedgerQuery
import java.math.BigDecimal
import java.time.LocalDateTime

interface LedgerDatabase {
    fun insertJournalEntry(createJournalEntry: CreateJournalEntry, postedAt: LocalDateTime? = null)
    fun getBalance(walletId: String, ledgerQueries: List<LedgerQuery>): BigDecimal
}