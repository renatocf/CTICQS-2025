package core.domain.model

import core.domain.enums.BalanceType.BalanceType
import core.domain.enums.SubwalletType.SubwalletType

case class LedgerQuery(
  subwalletType: SubwalletType,
  balanceType: BalanceType,
)
