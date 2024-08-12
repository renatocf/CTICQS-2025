package digitalwallet.data.models

import digitalwallet.data.common.BalanceConfig
import digitalwallet.services.LedgerService
import java.math.BigDecimal

class Balance(
    private val balanceConfigs: List<BalanceConfig>,

    ) {
    fun getAvailableBalance(ledgerService: LedgerService, walletId: String) : BigDecimal {
        return ledgerService.getBalance(walletId, this.balanceConfigs)
    }
}