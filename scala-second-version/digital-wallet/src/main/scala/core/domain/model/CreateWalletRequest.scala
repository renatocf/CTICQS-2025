package core.domain.model

import core.domain.enums.WalletType.WalletType

case class CreateWalletRequest(
  customerId: String,
  walletType: WalletType,
  policyId: Option[String],
)
