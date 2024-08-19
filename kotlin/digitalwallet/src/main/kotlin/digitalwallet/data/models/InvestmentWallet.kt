package digitalwallet.data.models

import digitalwallet.data.common.BalanceConfig
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.WalletOwnership
import digitalwallet.services.LedgerService
import java.math.BigDecimal
import java.time.LocalDateTime

class InvestmentWallet(
    id: String,
    customerId: String,
    policyId: String,
    insertedAt: LocalDateTime,
    ) : Wallet(id, customerId, policyId, insertedAt) {
    override fun getAvailableBalance(ledgerService: LedgerService): BigDecimal {
        val balanceConfig =  listOf(
            BalanceConfig(
                subwalletType = SubwalletType.BONDS,
                balanceType = BalanceType.AVAILABLE,
            ),
            BalanceConfig(
                subwalletType = SubwalletType.STOCK,
                balanceType = BalanceType.AVAILABLE,
            ),
            BalanceConfig(
                subwalletType = SubwalletType.REAL_ESTATE,
                balanceType = BalanceType.AVAILABLE,
            ),
            BalanceConfig(
                subwalletType = SubwalletType.CRYPTOCURRENCY,
                balanceType = BalanceType.AVAILABLE,
            )
        )

        return ledgerService.getBalance(this.id, balanceConfig)
    }

}