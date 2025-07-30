package digitalwallet.ports

import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.CreateWalletRequest
import digitalwallet.core.domain.models.Wallet as WalletModel

data class WalletFilter(
    val customerId: String? = null,
    val type: WalletType? = null,
)

interface WalletsDatabase {
    fun insert(request: CreateWalletRequest): WalletModel

    fun findById(id: String): WalletModel?

    fun find(filter: WalletFilter): List<WalletModel>
}
