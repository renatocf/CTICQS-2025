package digitalwallet.services

import digitalwallet.data.common.InvestmentRequest
import digitalwallet.data.common.LiquidationRequest
import digitalwallet.data.common.Logger
import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.TransactionFailed
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.data.enums.TransactionType
import digitalwallet.data.enums.WalletType
import digitalwallet.repo.InvestmentPolicyRepo
import digitalwallet.repo.WalletFilter
import digitalwallet.repo.WalletsRepo

class InvestmentFailed(message: String) : Exception()
class LiquidationFailed(message: String) : Exception()

class WalletsService(
    private val walletsRepo: WalletsRepo,
    private val investmentPolicyRepo: InvestmentPolicyRepo,
    private val transactionsService: TransactionsService,
    private val investmentService: InvestmentService,
) {
    private val logger = Logger()

    suspend fun invest(request: InvestmentRequest) {
        val wallet = walletsRepo.find(
            WalletFilter(
                customerId = request.customerId,
                type = WalletType.REAL_MONEY
            )
        ).firstOrNull() ?: throw NoSuchElementException("Wallet not found for customer ${request.customerId}")

        val processTransactionRequest = ProcessTransactionRequest(
            amount = request.amount,
            idempotencyKey = request.idempotencyKey,
            originatorWalletId = wallet.id,
            originatorSubwalletType = SubwalletType.REAL_MONEY,
            type = TransactionType.HOLD,
        )

        val transaction = transactionsService.processTransaction(processTransactionRequest)

        if (transaction.status != TransactionStatus.PROCESSING) {
            throw InvestmentFailed("Failed to process investment ${request.idempotencyKey}")
        }
    }

    suspend fun liquidate(request: LiquidationRequest) {
        val wallet = walletsRepo.find(
            WalletFilter(
                customerId = request.customerId,
                type = WalletType.INVESTMENT
            )
        ) .firstOrNull() ?: throw NoSuchElementException("Wallet not found for customer ${request.customerId}")
        val investmentPolicy = investmentPolicyRepo.findById(wallet.policyId) ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")

        try {
        investmentService.holdWithPolicy(
                InvestmentMovementRequest(
                amount = request.amount,
                idempotencyKey = request.idempotencyKey,
                walletId = wallet.id,
                investmentPolicy = investmentPolicy,
               )
            )
        } catch (e: TransactionFailed) {
            throw LiquidationFailed(e.message.toString())
        }
    }
}