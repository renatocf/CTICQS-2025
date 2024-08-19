package digitalwallet.repo

import digitalwallet.data.enums.WalletType
import digitalwallet.repo.data.Wallet
import digitalwallet.data.models.Wallet as WalletModel

data class WalletFilter(
    val customerId: String? = null,
    val type: WalletType? = null,
)

class WalletsRepo {
    private val wallets = mutableMapOf<String, Wallet>()

    fun findById(id: String): WalletModel? {
        return wallets[id]?.toModel()
    }

    fun find(filter: WalletFilter): List<WalletModel> {
        return wallets.values.filter { wallet ->
            (filter.customerId?.let { it == wallet.customerId } ?: true) &&
                    (filter.type?.let { it == wallet.type } ?: true)
        }.map { it.toModel() }
    }
}