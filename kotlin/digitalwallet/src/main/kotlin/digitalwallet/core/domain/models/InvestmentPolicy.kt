package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.SubwalletType
import java.math.BigDecimal

data class InvestmentPolicy(
    val id: String,
    val allocationStrategy: Map<SubwalletType, BigDecimal>
)