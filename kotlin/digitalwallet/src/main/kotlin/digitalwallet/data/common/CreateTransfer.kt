package digitalwallet.data.common

import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class CreateTransfer(
    val sourceWalletId: String,
    val sourceSubwalletType: SubwalletType,
    val targetWalletId: String,
    val targetSubwalletType: SubwalletType,
    val amount: BigDecimal,
)