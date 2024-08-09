package digitalwallet.data.models

import digitalwallet.data.enums.WalletOwnership
import digitalwallet.services.LedgerService
import jakarta.inject.Inject
import java.math.BigDecimal
import java.time.LocalDateTime

abstract class Wallet(
    val id: String,
    val ownership: WalletOwnership,
    val policyId: String,
    val insertedAt: LocalDateTime,

    @Inject
    val ledgerService: LedgerService
) {
    abstract fun getAvailableBalance() : BigDecimal
}