package adapters

import core.domain.entities.Wallet
import core.domain.model.CreateWalletRequest
import ports.{WalletFilter, WalletsDatabase}

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.mutable

class WalletsInMemoryDatabase extends WalletsDatabase {
  private val wallets: mutable.Map[String, Wallet] = mutable.Map()

  override def insert(request: CreateWalletRequest): Wallet = {
    val id = UUID.randomUUID().toString

    val wallet = Wallet(
      id = id,
      customerId = request.customerId,
      walletType = request.walletType,
      policyId = request.policyId,
      insertedAt = LocalDateTime.now()
    )

    wallets(id) = wallet
    wallet
  }

  override def findById(id: String): Option[Wallet] = wallets.get(id)

  override def find(filter: WalletFilter): List[Wallet] = {
    wallets.values
      .filter(wallet =>
        filter.customerId.forall(_ == wallet.customerId) &&
          filter.walletType.forall(_ == wallet.walletType)
      )
      .toList
  }

  def clear(): Unit = {
    wallets.clear()
  }
}