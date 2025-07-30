package core.domain.entities

import core.domain.enums.BalanceType.BalanceType
import core.domain.enums.SubwalletType.SubwalletType

case class JournalEntry(
  id: String,
  walletId: Option[String],
  subwalletType: SubwalletType,
  balanceType: BalanceType,
  amount: BigDecimal,
)
