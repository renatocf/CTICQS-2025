package digitalwallet.data.models

import digitalwallet.data.common.BalanceConfig
import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.WalletOwnership
import digitalwallet.repo.WalletsRepo
import digitalwallet.services.LedgerService
import java.math.BigDecimal
import java.time.LocalDateTime

class EmergencyFundsWallet(
    id: String,
    ownership: WalletOwnership,
    policyId: String,
    insertedAt: LocalDateTime,

    private val ledgerService: LedgerService,
) : Wallet(id, ownership, policyId, insertedAt) {
    override fun getAvailableBalance(): BigDecimal {
        val balanceConfig =  listOf(BalanceConfig(
            subwalletType = SubwalletType.EMERGENCY_FUND,
            balanceType = BalanceType.AVAILABLE,
            )
        )

        return ledgerService.getBalance(this.id, balanceConfig)
    }

}