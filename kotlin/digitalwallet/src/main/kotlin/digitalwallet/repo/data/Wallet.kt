package digitalwallet.repo.data

import digitalwallet.data.enums.WalletOwnership
import digitalwallet.data.enums.WalletType
import digitalwallet.data.models.EmergencyFundsWallet
import digitalwallet.data.models.InvestmentWallet
import digitalwallet.data.models.RealMoneyWallet
import digitalwallet.data.models.Wallet as WalletDto
import java.time.LocalDateTime

data class Wallet(
    val id: String,
    val customerId: String,
    val type: WalletType,
    val policyId: String,
    val insertedAt: LocalDateTime
) {
    fun toModel() : WalletDto {
        return when (this.type) {
            WalletType.REAL_MONEY -> RealMoneyWallet(
                id = this.id,
                customerId = this.customerId,
                 policyId = this.policyId,
                insertedAt = this.insertedAt
            )
            WalletType.INVESTMENT -> InvestmentWallet(
                id = this.id,
                customerId = this.customerId,
                policyId = this.policyId,
                insertedAt = this.insertedAt
            )
            WalletType.EMERGENCY_FUND -> EmergencyFundsWallet(
                id = this.id,
                customerId = this.customerId,
                policyId = this.policyId,
                insertedAt = this.insertedAt
            )
        }
    }
}