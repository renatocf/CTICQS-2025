package digitalwallet.services

import digitalwallet.data.common.InvestmentRequest
import digitalwallet.data.common.LiquidationRequest
import digitalwallet.data.common.Logger
import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.TransactionFailed
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.data.enums.TransactionType
import digitalwallet.repo.InvestmentPolicyRepo
import digitalwallet.repo.WalletsRepo

class WalletsService(
    private val walletsRepo: WalletsRepo,
    private val investmentPolicyRepo: InvestmentPolicyRepo,
    private val transactionsService: TransactionsService,
    private val investmentService: InvestmentService,
) {
    private val logger = Logger()

    suspend fun invest(request: InvestmentRequest) {
        val wallet = walletsRepo.findById(request.walletId)?.dto() ?: throw NoSuchElementException("Wallet ${request.walletId} not found")

        // check if it is real money

        val processTransactionRequest = ProcessTransactionRequest(
            amount = request.amount,
            idempotencyKey = request.idempotencyKey,
            originatorWalletId = request.walletId,
            originatorSubwalletType = SubwalletType.REAL_MONEY,
            type = TransactionType.HOLD,
        )

        transactionsService.processTransaction(processTransactionRequest)

        // we should test if transfer is processing...
    }

    suspend fun liquidate(request: LiquidationRequest) {
        val wallet = walletsRepo.findById(request.walletId)?.dto() ?: throw NoSuchElementException("Wallet ${request.walletId} not found")
        val investmentPolicy = investmentPolicyRepo.findById(wallet.policyId)?.dto() ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")

        // should we check if investment policy is consistent?

        try {
            investmentService.processHoldBatchWithInvestmentPolicy(
                ProcessBatchWithInvestmentPolicy(
                amount = request.amount,
                idempotencyKey = request.idempotencyKey,
                walletId = request.walletId,
                investmentPolicy = investmentPolicy,
            )
            )
        } catch (e: TransactionFailed) {
            val message = e.message.toString()
            logger.error(message)
            transactionsService.reverseAndFailTransactionsBatch(request.idempotencyKey)
        }
            // we should test if transfer is processing...
            // should we hold atomically? If so, is it actually complicated to do this in Kotlin?
    }
}