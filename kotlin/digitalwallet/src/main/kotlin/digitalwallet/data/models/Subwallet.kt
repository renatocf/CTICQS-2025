package digitalwallet.data.models

import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.Currency
import digitalwallet.data.enums.Location
import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class Subwallet(
    val id: String,
    val type: SubwalletType,
    val location: Location,
    val balanceType: BalanceType,
    val amount: BigDecimal,
    val currency: Currency,
)