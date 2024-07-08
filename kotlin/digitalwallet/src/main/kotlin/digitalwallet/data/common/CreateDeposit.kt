package digitalwallet.data.common

import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class CreateDeposit(
    val walletId: String,
    val subwalletType: SubwalletType,
    val amount: BigDecimal,
)