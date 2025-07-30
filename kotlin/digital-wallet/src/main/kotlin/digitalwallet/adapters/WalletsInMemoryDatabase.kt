package digitalwallet.adapters

import digitalwallet.core.domain.entities.Wallet
import digitalwallet.core.domain.models.CreateWalletRequest
import digitalwallet.ports.WalletFilter
import digitalwallet.ports.WalletsDatabase
import java.time.LocalDateTime
import java.util.UUID
import digitalwallet.core.domain.models.Wallet as WalletModel

class WalletsInMemoryDatabase : WalletsDatabase {
    val wallets = mutableMapOf<String, Wallet>()

    override fun insert(request: CreateWalletRequest): WalletModel {
        val id = UUID.randomUUID().toString()

        val wallet =
            Wallet(
                id = id,
                customerId = request.customerId,
                type = request.type,
                policyId = request.policyId,
                insertedAt = LocalDateTime.now(),
            )

        wallets[id] = wallet

        return wallet.toModel()
    }

    override fun findById(id: String): WalletModel? = wallets[id]?.toModel()

    override fun find(filter: WalletFilter): List<WalletModel> =
        wallets.values
            .filter { wallet ->
                (filter.customerId?.let { it == wallet.customerId } ?: true) &&
                    (filter.type?.let { it == wallet.type } ?: true)
            }.map { it.toModel() }

    fun clear() {
        wallets.clear()
    }
}
