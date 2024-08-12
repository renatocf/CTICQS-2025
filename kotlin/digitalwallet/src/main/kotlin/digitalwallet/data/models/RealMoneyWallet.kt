package digitalwallet.data.models

import digitalwallet.data.common.BalanceConfig
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.WalletOwnership
import digitalwallet.services.LedgerService
import java.math.BigDecimal
import java.time.LocalDateTime

class RealMoneyWallet(
    id: String,
    ownership: WalletOwnership,
    policyId: String,
    insertedAt: LocalDateTime,
) : Wallet(id, ownership, policyId, insertedAt) {
    override fun getAvailableBalance(ledgerService: LedgerService): BigDecimal {
        val balanceConfig = listOf(BalanceConfig(
            subwalletType = SubwalletType.REAL_MONEY,
            balanceType = BalanceType.AVAILABLE,
            )
        )

        return ledgerService.getBalance(this.id, balanceConfig)
    }
}