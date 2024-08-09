package digitalwallet.data.common

import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType

data class BalanceConfig(
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
)