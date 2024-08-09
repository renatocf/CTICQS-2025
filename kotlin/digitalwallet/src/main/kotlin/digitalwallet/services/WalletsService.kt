package digitalwallet.services

import digitalwallet.data.common.InvestmentRequest
import digitalwallet.data.common.LiquidationRequest
import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionType
import digitalwallet.repo.InvestmentPolicyRepo
import digitalwallet.repo.WalletsRepo

class WalletsService(
    val walletsRepo: WalletsRepo,
    val investmentPolicyRepo: InvestmentPolicyRepo,
    val transactionsService: TransactionsService,
) {
    fun invest(request: InvestmentRequest) {
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

    fun liquidate(request: LiquidationRequest) {
        val wallet = walletsRepo.findById(request.walletId)?.dto() ?: throw NoSuchElementException("Wallet ${request.walletId} not found")
        val investmentPolicy = investmentPolicyRepo.findById(wallet.policyId)?.dto() ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")

        // should we check if investment policy is consistent?

        for ((subwalletType, percentage) in investmentPolicy.allocationStrategy) {
            val amount = request.amount * percentage

            val processTransactionRequest = ProcessTransactionRequest(
                amount = amount,
                idempotencyKey = request.idempotencyKey,
                originatorWalletId = request.walletId,
                originatorSubwalletType = subwalletType,
                type = TransactionType.HOLD,
            )

            transactionsService.processTransaction(processTransactionRequest = processTransactionRequest)

            // we should test if transfer is processing...
            // should we hold atomically? If so, is it actually complicated to do this in Kotlin?
        }
    }
}