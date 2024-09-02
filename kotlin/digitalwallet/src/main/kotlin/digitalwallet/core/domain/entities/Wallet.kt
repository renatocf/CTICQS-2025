package digitalwallet.core.domain.entities

import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.EmergencyFundsWallet
import digitalwallet.core.domain.models.InvestmentWallet
import digitalwallet.core.domain.models.RealMoneyWallet
import java.time.LocalDateTime
import digitalwallet.core.domain.models.Wallet as WalletModel

data class Wallet(
    val id: String,
    val customerId: String,
    val type: WalletType,
    val policyId: String,
    val insertedAt: LocalDateTime,
) {
    fun toModel(): WalletModel =
        when (this.type) {
            WalletType.REAL_MONEY ->
                RealMoneyWallet(
                    id = this.id,
                    customerId = this.customerId,
                    policyId = this.policyId,
                    insertedAt = this.insertedAt,
                )
            WalletType.INVESTMENT ->
                InvestmentWallet(
                    id = this.id,
                    customerId = this.customerId,
                    policyId = this.policyId,
                    insertedAt = this.insertedAt,
                )
            WalletType.EMERGENCY_FUND ->
                EmergencyFundsWallet(
                    id = this.id,
                    customerId = this.customerId,
                    policyId = this.policyId,
                    insertedAt = this.insertedAt,
                )
        }
}
