package digitalwallet.core.domain.models

import digitalwallet.core.services.LedgerService
import java.math.BigDecimal

class Balance(
    private val ledgerQueries: List<LedgerQuery>,

    ) {
    fun getAvailableBalance(ledgerService: LedgerService, walletId: String) : BigDecimal {
        return ledgerService.getBalance(walletId, this.ledgerQueries)
    }
}