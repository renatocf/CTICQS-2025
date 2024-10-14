package core.domain.model

case class LiquidationRequest(
  customerId: String,
  amount: BigDecimal,
  idempotencyKey: String,
)
