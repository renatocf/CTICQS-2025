package digitalwallet.data.common

import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class CreateHold(
    val walletId: String,
    val subwalletType: SubwalletType,
    val amount: BigDecimal,
)