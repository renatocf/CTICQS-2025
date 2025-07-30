package digitalwallet.core.services

import digitalwallet.core.domain.models.Transaction
import jakarta.inject.Singleton

@Singleton
class PartnerService {
    suspend fun executeInternalTransfer(transaction: Transaction) {
        return Unit
    }
}