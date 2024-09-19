package ports

import core.domain.model.{CreateJournalEntry, LedgerQuery}

import java.time.LocalDateTime

trait LedgerDatabase {
  def insertJournalEntry(createJournalEntry: CreateJournalEntry, postedAt: Option[LocalDateTime] = None): Unit
  def getBalance(walletId: String, ledgerQueries: List[LedgerQuery]): BigDecimal
}
