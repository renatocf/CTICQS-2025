package core.domain.entities

import core.domain.enums.SubwalletType.SubwalletType

case class InvestmentPolicy(
  id: String,
  allocationStrategy: Map[SubwalletType, BigDecimal],
)
