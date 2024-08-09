package digitalwallet.repo

import digitalwallet.repo.data.InvestmentPolicy

class InvestmentPolicyRepo {
    private val investmentPolicies = mutableMapOf<String, InvestmentPolicy>()

    fun findById(id: String): InvestmentPolicy? {
        return investmentPolicies[id]
    }
}