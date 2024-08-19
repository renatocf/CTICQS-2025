package digitalwallet.data.common

import java.math.BigDecimal

data class LiquidationRequest(
    val customerId: String,
    val amount: BigDecimal,
    val idempotencyKey: String,
)