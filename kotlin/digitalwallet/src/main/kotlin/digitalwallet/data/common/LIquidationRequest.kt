package digitalwallet.data.common

import java.math.BigDecimal

data class LiquidationRequest(
    val walletId: String,
    val amount: BigDecimal,
    val idempotencyKey: String,
)