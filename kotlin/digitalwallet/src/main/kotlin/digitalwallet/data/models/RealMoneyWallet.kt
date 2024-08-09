package digitalwallet.data.models

import digitalwallet.data.common.BalanceConfig
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.WalletOwnership
import digitalwallet.data.enums.WalletType
import digitalwallet.services.LedgerService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDateTime

@Singleton
class RealMoneyWallet(
    id: String,
    ownership: WalletOwnership,
    policyId: String,
    insertedAt: LocalDateTime,
    ledgerService: LedgerService
) : Wallet(id, ownership, policyId, insertedAt, ledgerService) {
    override fun getAvailableBalance(): BigDecimal {
        val balanceConfig = listOf(BalanceConfig(
            subwalletType = SubwalletType.REAL_MONEY,
            balanceType = BalanceType.AVAILABLE,
            )
        )

        return ledgerService.getBalance(this.id, balanceConfig)
    }
}