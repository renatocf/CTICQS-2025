package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.services.LedgerService
import java.math.BigDecimal
import java.time.LocalDateTime

class RealMoneyWallet(
    id: String,
    customerId: String,
    policyId: String,
    insertedAt: LocalDateTime,
) : Wallet(id, customerId, policyId, insertedAt) {
    override fun getAvailableBalance(ledgerService: LedgerService): BigDecimal {
        val ledgerQuery = listOf(
            LedgerQuery(
            subwalletType = SubwalletType.REAL_MONEY,
            balanceType = BalanceType.AVAILABLE,
            )
        )

        return ledgerService.getBalance(this.id, ledgerQuery)
    }
}