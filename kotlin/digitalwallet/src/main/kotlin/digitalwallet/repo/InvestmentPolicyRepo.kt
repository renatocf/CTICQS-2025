package digitalwallet.repo

import digitalwallet.repo.data.InvestmentPolicy
import digitalwallet.data.models.InvestmentPolicy as InvestmentPolicyModel

class InvestmentPolicyRepo {
    private val investmentPolicies = mutableMapOf<String, InvestmentPolicy>()

    fun findById(id: String): InvestmentPolicyModel? {
        return investmentPolicies[id]?.toModel()
    }
}