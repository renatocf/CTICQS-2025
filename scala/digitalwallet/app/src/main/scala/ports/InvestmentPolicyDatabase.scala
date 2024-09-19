package ports

import core.domain.entities.InvestmentPolicy
import core.domain.model.CreateInvestmentPolicyRequest

trait InvestmentPolicyDatabase {
  def insert(request: CreateInvestmentPolicyRequest): InvestmentPolicy

  def findById(id: String): InvestmentPolicy
}
