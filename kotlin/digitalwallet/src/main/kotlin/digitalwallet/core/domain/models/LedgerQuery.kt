package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType

data class LedgerQuery(
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
)