package ports

import core.domain.entities.Wallet
import core.domain.enums.WalletType.WalletType
import core.domain.model.CreateWalletRequest

case class WalletFilter(
 customerId: Option[String] = None,
 walletTYpe:  Option[WalletType] = None,
)

trait WalletsDatabase {
  def insert(request: CreateWalletRequest): Wallet

  def findById(id: String): Wallet

  def find(filter: WalletFilter): List[Wallet]
}
