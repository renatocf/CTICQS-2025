package digitalwallet.data.common

import java.math.BigDecimal

data class InvestmentRequest(
    val walletId: String,
    val amount: BigDecimal,
    val idempotencyKey: String,
)