package core.domain.model

import core.domain.enums.SubwalletType.SubwalletType

case class CreateInvestmentPolicyRequest(
  allocationStrategy: Map[SubwalletType, BigDecimal],
)
