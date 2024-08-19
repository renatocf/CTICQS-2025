package digitalwallet.repo.data

import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import java.math.BigDecimal

data class JournalEntry(
    val id: String,
    val walletId: String?,
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
    val amount: BigDecimal,
)