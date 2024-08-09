package digitalwallet.data.models

import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class InvestmentPolicy(
    val id: String,
    val allocationStrategy: Map<SubwalletType, BigDecimal>
)