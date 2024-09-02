package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.SubwalletType
import java.math.BigDecimal

data class CreateInvestmentPolicyRequest(
    val allocationStrategy: MutableMap<SubwalletType, BigDecimal>,
)
