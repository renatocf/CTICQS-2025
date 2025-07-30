package digitalwallet.core.domain.models

import digitalwallet.core.services.LedgerService
import java.math.BigDecimal
import java.time.LocalDateTime

abstract class Wallet(
    val id: String,
    val customerId: String,
    val policyId: String,
    val insertedAt: LocalDateTime,
) {
    abstract fun getAvailableBalance(ledgerService: LedgerService) : BigDecimal
}