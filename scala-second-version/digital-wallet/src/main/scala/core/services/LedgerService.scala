package core.services

import core.domain.model.{CreateJournalEntry, LedgerQuery}
import ports.LedgerDatabase

import java.time.LocalDateTime

class LedgerService(ledgerRepo: LedgerDatabase) {

  def postJournalEntries(journalEntries: List[CreateJournalEntry]): LocalDateTime = {
    val postedAt = LocalDateTime.now()

    journalEntries.foreach { journalEntry =>
      ledgerRepo.insertJournalEntry(journalEntry, Some(postedAt))
    }

    postedAt
  }

  def getBalance(walletId: String, ledgerQuery: List[LedgerQuery]): BigDecimal = {
    ledgerRepo.getBalance(walletId, ledgerQuery)
  }
}
