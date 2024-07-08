package digitalwallet.data.models

import digitalwallet.data.enums.WalletOwnership
import digitalwallet.data.enums.WalletType

data class Wallet(
    val id: String,
    val ownership: WalletOwnership,
    val type: WalletType,
    val policyId: String,
)