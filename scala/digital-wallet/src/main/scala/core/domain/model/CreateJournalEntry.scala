package core.domain.model

import core.domain.enums.BalanceType.BalanceType
import core.domain.enums.SubwalletType.SubwalletType

case class CreateJournalEntry(
  walletId: Option[String],
  subwalletType: SubwalletType,
  balanceType: BalanceType,
  amount: BigDecimal,
)
