package adapters

import core.domain.entities.JournalEntry
import core.domain.model.{CreateJournalEntry, LedgerQuery}
import ports.LedgerDatabase

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.mutable

class LedgerInMemoryDatabase extends LedgerDatabase {
  private val journalEntries: mutable.Map[String, JournalEntry] = mutable.Map()

  override def insertJournalEntry(createJournalEntry: CreateJournalEntry, postedAt: Option[LocalDateTime]): Unit = {
    val id = UUID.randomUUID().toString
    val journalEntry = JournalEntry(
      id = id,
      walletId = createJournalEntry.walletId,
      amount = createJournalEntry.amount,
      subwalletType = createJournalEntry.subwalletType,
      balanceType = createJournalEntry.balanceType
    )

    journalEntries(id) = journalEntry
  }

  override def getBalance(walletId: String, ledgerQueries: List[LedgerQuery]): BigDecimal = {
    ledgerQueries.flatMap { query =>
      journalEntries.values.filter { entry =>
        entry.walletId.contains(walletId) && entry.subwalletType == query.subwalletType && entry.balanceType == query.balanceType
      }
    }.map(_.amount).sum
  }

  def clear(): Unit = {
    journalEntries.clear()
  }
}
