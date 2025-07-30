package core.domain.entities

import core.domain.enums.WalletType.WalletType

import java.time.LocalDateTime

case class Wallet(
  val id: String,
  val customerId: String,
  val walletType: WalletType,
  val policyId: Option[String],
  val insertedAt: LocalDateTime,
)
