package adapters

import core.domain.entities.InvestmentPolicy
import core.domain.model.CreateInvestmentPolicyRequest
import ports.InvestmentPolicyDatabase

import java.util.UUID
import scala.collection.mutable

class InvestmentPolicyInMemoryDatabase extends InvestmentPolicyDatabase {
  val investmentPolicies: mutable.Map[String, InvestmentPolicy] = mutable.Map()

  override def insert(request: CreateInvestmentPolicyRequest): InvestmentPolicy = {
    val id = UUID.randomUUID().toString

    val investmentPolicy = InvestmentPolicy(
      id = id,
      allocationStrategy = request.allocationStrategy
    )

    investmentPolicies(id) = investmentPolicy
    investmentPolicy
  }

  override def findById(id: String): Option[InvestmentPolicy] = investmentPolicies.get(id)

  def clear(): Unit = {
    investmentPolicies.clear()
  }
}
