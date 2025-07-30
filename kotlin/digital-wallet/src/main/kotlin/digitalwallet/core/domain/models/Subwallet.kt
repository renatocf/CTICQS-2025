package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.Currency
import digitalwallet.core.domain.enums.Location
import digitalwallet.core.domain.enums.SubwalletType
import java.math.BigDecimal

data class Subwallet(
    val id: String,
    val type: SubwalletType,
    val location: Location,
    val balanceType: BalanceType,
    val amount: BigDecimal,
    val currency: Currency,
)