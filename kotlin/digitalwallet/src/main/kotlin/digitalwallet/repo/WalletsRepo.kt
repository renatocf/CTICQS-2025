package digitalwallet.repo

import digitalwallet.repo.data.Wallet

class WalletsRepo {
    private val wallets = mutableMapOf<String, Wallet>()

    fun findById(id: String): Wallet? {
        return wallets[id]
    }
}