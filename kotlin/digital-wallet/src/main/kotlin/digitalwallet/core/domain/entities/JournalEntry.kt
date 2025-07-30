package digitalwallet.core.domain.entities

import java.math.BigDecimal
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.BalanceType

data class JournalEntry(
    val id: String,
    val walletId: String?,
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
    val amount: BigDecimal,
)