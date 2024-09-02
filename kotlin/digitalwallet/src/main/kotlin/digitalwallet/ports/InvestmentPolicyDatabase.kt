package digitalwallet.ports

import digitalwallet.core.domain.models.CreateInvestmentPolicyRequest
import digitalwallet.core.domain.models.InvestmentPolicy as InvestmentPolicyModel

interface InvestmentPolicyDatabase {
    fun insert(request: CreateInvestmentPolicyRequest): InvestmentPolicyModel

    fun findById(id: String): InvestmentPolicyModel?
}
