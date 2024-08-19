package digitalwallet.repo.data

import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.models.InvestmentPolicy as InvestmentPolicyDto
import java.math.BigDecimal

data class InvestmentPolicy(
    val id: String,
    val allocationStrategy: MutableMap<SubwalletType, BigDecimal>
) {
    fun toModel(): InvestmentPolicyDto {
        return InvestmentPolicyDto(
            id = this.id,
            allocationStrategy = this.allocationStrategy
        )
    }
}