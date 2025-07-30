package digitalwallet.core.domain.entities

import digitalwallet.core.domain.enums.SubwalletType
import java.math.BigDecimal
import digitalwallet.core.domain.models.InvestmentPolicy as InvestmentPolicyModel

data class InvestmentPolicy(
    val id: String,
    val allocationStrategy: MutableMap<SubwalletType, BigDecimal>,
) {
    fun toModel(): InvestmentPolicyModel =
        InvestmentPolicyModel(
            id = this.id,
            allocationStrategy = this.allocationStrategy,
        )
}
