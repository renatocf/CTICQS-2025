package digitalwallet.adapters

import digitalwallet.core.domain.entities.InvestmentPolicy
import digitalwallet.core.domain.models.CreateInvestmentPolicyRequest
import digitalwallet.ports.InvestmentPolicyDatabase
import java.util.UUID
import digitalwallet.core.domain.models.InvestmentPolicy as InvestmentPolicyModel

class InvestmentPolicyInMemoryDatabase : InvestmentPolicyDatabase {
    val investmentPolicies = mutableMapOf<String, InvestmentPolicy>()

    override fun insert(request: CreateInvestmentPolicyRequest): InvestmentPolicyModel {
        val id = UUID.randomUUID().toString()

        val investmentPolicy =
            InvestmentPolicy(
                id = id,
                allocationStrategy = request.allocationStrategy,
            )

        investmentPolicies[id] = investmentPolicy

        return investmentPolicy.toModel()
    }

    override fun findById(id: String): InvestmentPolicyModel? = investmentPolicies[id]?.toModel()

    fun clear() {
        investmentPolicies.clear()
    }
}
