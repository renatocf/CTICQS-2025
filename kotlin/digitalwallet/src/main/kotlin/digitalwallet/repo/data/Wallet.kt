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
    val type: WalletType,
    val ownership: WalletOwnership,
    val policyId: String,
    val insertedAt: LocalDateTime
) {
    fun dto() : WalletDto {
        return when (this.type) {
            WalletType.REAL_MONEY -> RealMoneyWallet(
                id = this.id,
                ownership = this.ownership,
                policyId = this.policyId,
                insertedAt = this.insertedAt
            )
            WalletType.INVESTMENT -> InvestmentWallet(
                id = this.id,
                ownership = this.ownership,
                policyId = this.policyId,
                insertedAt = this.insertedAt
            )
            WalletType.EMERGENCY_FUND -> EmergencyFundsWallet(
                id = this.id,
                ownership = this.ownership,
                policyId = this.policyId,
                insertedAt = this.insertedAt
            )
        }
    }
}