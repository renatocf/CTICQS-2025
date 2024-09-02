package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.BalanceType
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.services.LedgerService
import java.math.BigDecimal
import java.time.LocalDateTime

class InvestmentWallet(
    id: String,
    customerId: String,
    policyId: String,
    insertedAt: LocalDateTime,
    ) : Wallet(id, customerId, policyId, insertedAt) {
    override fun getAvailableBalance(ledgerService: LedgerService): BigDecimal {
        val ledgerQueries =  listOf(
            LedgerQuery(
                subwalletType = SubwalletType.BONDS,
                balanceType = BalanceType.AVAILABLE,
            ),
            LedgerQuery(
                subwalletType = SubwalletType.STOCK,
                balanceType = BalanceType.AVAILABLE,
            ),
            LedgerQuery(
                subwalletType = SubwalletType.REAL_ESTATE,
                balanceType = BalanceType.AVAILABLE,
            ),
            LedgerQuery(
                subwalletType = SubwalletType.CRYPTOCURRENCY,
                balanceType = BalanceType.AVAILABLE,
            )
        )

        return ledgerService.getBalance(this.id, ledgerQueries)
    }

}