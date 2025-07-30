package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import java.math.BigDecimal

data class CreateJournalEntry(
    val walletId: String?,
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
    val amount: BigDecimal,
)