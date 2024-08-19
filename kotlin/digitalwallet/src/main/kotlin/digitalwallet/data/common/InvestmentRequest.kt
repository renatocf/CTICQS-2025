package digitalwallet.data.common

import java.math.BigDecimal

data class InvestmentRequest(
    val customerId: String,
    val amount: BigDecimal,
    val idempotencyKey: String,
)