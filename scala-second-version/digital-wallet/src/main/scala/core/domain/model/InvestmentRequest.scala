package core.domain.model

case class InvestmentRequest(
  customerId: String,
  amount: BigDecimal,
  idempotencyKey: String,
)
