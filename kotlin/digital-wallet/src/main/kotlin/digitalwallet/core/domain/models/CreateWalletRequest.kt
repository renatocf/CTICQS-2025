package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.WalletType

data class CreateWalletRequest(
    val customerId: String,
    val type: WalletType,
    val policyId: String,
)
