package digitalwallet.core.services

import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.InvestmentRequest
import digitalwallet.core.domain.models.LiquidationRequest
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.PartnerException
import digitalwallet.core.exceptions.TransactionFailed
import digitalwallet.core.exceptions.ValidationException
import digitalwallet.ports.InvestmentPolicyDatabase
import digitalwallet.ports.WalletFilter
import digitalwallet.ports.WalletsDatabase

class InvestmentFailed(
    message: String,
) : Exception()

class LiquidationFailed(
    message: String,
) : Exception()

class WalletsService(
    private val walletsRepo: WalletsDatabase,
    private val investmentPolicyRepo: InvestmentPolicyDatabase,
    private val transactionsService: TransactionsService,
    private val investmentService: InvestmentService,
) {
    suspend fun invest(request: InvestmentRequest) {
        val wallet =
            walletsRepo
                .find(
                    WalletFilter(
                        customerId = request.customerId,
                        type = WalletType.REAL_MONEY,
                    ),
                ).firstOrNull() ?: throw NoSuchElementException("Wallet not found for customer ${request.customerId}")

        val processTransactionRequest =
            ProcessTransactionRequest(
                amount = request.amount,
                idempotencyKey = request.idempotencyKey,
                originatorWalletId = wallet.id,
                originatorSubwalletType = SubwalletType.REAL_MONEY,
                type = TransactionType.HOLD,
            )

        try {
            transactionsService.processTransaction(processTransactionRequest)
        } catch (e: ValidationException) {
            println(e)
            transactionsService.handleException(e, TransactionStatus.FAILED, request.idempotencyKey)
            throw InvestmentFailed(e.message.toString())
        } catch (e: PartnerException) {
            transactionsService.handleException(e, TransactionStatus.TRANSIENT_ERROR, request.idempotencyKey)
        }
    }

    suspend fun liquidate(request: LiquidationRequest) {
        val wallet =
            walletsRepo
                .find(
                    WalletFilter(
                        customerId = request.customerId,
                        type = WalletType.INVESTMENT,
                    ),
                ).firstOrNull() ?: throw NoSuchElementException("Wallet not found for customer ${request.customerId}")
        val investmentPolicy =
            investmentPolicyRepo.findById(wallet.policyId) ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")

        try {
            investmentService.executeMovementWithInvestmentPolicy(
                InvestmentMovementRequest(
                    amount = request.amount,
                    idempotencyKey = request.idempotencyKey,
                    walletId = wallet.id,
                    investmentPolicy = investmentPolicy,
                    transactionType = TransactionType.HOLD,
                ),
            )
        } catch (e: TransactionFailed) {
            throw LiquidationFailed(e.message.toString())
        }
    }
}
