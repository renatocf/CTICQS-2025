package core.domain.model

import core.domain.entities.InvestmentPolicy
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.TransactionType.TransactionType

case class MovementRequest(
  amount: BigDecimal,
  idempotencyKey: String,
  walletId: String,
  walletSubwalletType: Option[SubwalletType] = None,
  targetWalletId: Option[String] = None,
  investmentPolicy: InvestmentPolicy,
  transactionType: TransactionType,
)
