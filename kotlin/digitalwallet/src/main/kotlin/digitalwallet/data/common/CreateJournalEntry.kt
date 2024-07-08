package digitalwallet.data.common

import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class CreateJournalEntry(
    val walletId: String?,
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
    val amount: BigDecimal,
)