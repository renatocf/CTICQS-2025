package digitalwallet.services

import digitalwallet.data.common.Logger
import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.TransactionFailed
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.data.enums.TransactionType
import digitalwallet.data.models.InvestmentPolicy
import digitalwallet.repo.InvestmentPolicyRepo
import digitalwallet.repo.TransactionFilter
import digitalwallet.repo.TransactionsRepo
import digitalwallet.repo.WalletsRepo
import java.math.BigDecimal

data class InvestmentMovementRequest(
    val amount: BigDecimal,
    val idempotencyKey: String,
    val walletId: String,
    val investmentPolicy: InvestmentPolicy,
)

class InvestmentService(
    private val transactionsRepo: TransactionsRepo,
    private val walletRepo: WalletsRepo,
    private val investmentPolicyRepo: InvestmentPolicyRepo,
    private val transactionsService: TransactionsService,
    private val ledgerService: LedgerService,
) {
    private val logger = Logger()

    suspend fun holdWithPolicy(request: InvestmentMovementRequest) {
        for ((subwalletType, percentage) in request.investmentPolicy.allocationStrategy) {
            val processTransactionRequest = ProcessTransactionRequest(
                amount = request.amount * percentage,
                batchId = request.idempotencyKey,
                idempotencyKey = "${request.idempotencyKey}_$subwalletType}",
                originatorWalletId = request.walletId,
                originatorSubwalletType = subwalletType,
                type = TransactionType.HOLD,
            )

            val transaction = transactionsService.processTransaction(processTransactionRequest)

            if (transaction.status != TransactionStatus.PROCESSING) {
                logger.error("Investment movement failed for request ${request.idempotencyKey}")
                transactionsService.reverseAndFailTransactionsBatch(request.idempotencyKey)
                throw TransactionFailed("Transaction ${transaction.id} failed")
            }
        }
    }

    private suspend fun transferWithPolicy(request: InvestmentMovementRequest) {
        for ((subwalletType, percentage) in request.investmentPolicy.allocationStrategy) {
            val processTransactionRequest = ProcessTransactionRequest(
                amount = request.amount * percentage,
                batchId = request.idempotencyKey,
                idempotencyKey = "${request.idempotencyKey}_$subwalletType}",
                originatorWalletId = request.walletId,
                originatorSubwalletType = subwalletType,
                type = TransactionType.TRANSFER_FROM_HOLD,
            )

            val transaction = transactionsService.processTransaction(processTransactionRequest)

            if (transaction.status != TransactionStatus.COMPLETED) {
                logger.error("Investment movement failed for request ${request.idempotencyKey}")
                transactionsService.reverseAndFailTransactionsBatch(request.idempotencyKey)
                throw TransactionFailed("Transaction ${transaction.id} failed")
            }
        }
    }

    suspend fun buyFunds() {
        val transactions = transactionsRepo.find(
            TransactionFilter(
                status = TransactionStatus.PROCESSING,
                subwalletType = listOf(SubwalletType.REAL_MONEY),
            )
        )

        for (investmentTransaction in transactions) {
            val wallet = walletRepo.findById(investmentTransaction.originatorWalletId)
                ?: throw NoSuchElementException("Wallet ${investmentTransaction.originatorWalletId} not found")
            val investmentPolicy = investmentPolicyRepo.findById(wallet.policyId)
                ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")

            try {
                transferWithPolicy(
                    InvestmentMovementRequest(
                        amount = investmentTransaction.amount,
                        idempotencyKey = investmentTransaction.id,
                        walletId = investmentTransaction.originatorWalletId,
                        investmentPolicy = investmentPolicy,
                    )
                )

                investmentTransaction.updateStatus(transactionsRepo, newStatus = TransactionStatus.COMPLETED)
            } catch (e: TransactionFailed) {
                val message = e.message.toString()
                logger.error(message)
                investmentTransaction.reverse(ledgerService)
                investmentTransaction.updateStatus(
                    transactionsRepo,
                    newStatus = TransactionStatus.FAILED,
                    statusReason = message
                )
            }
        }
    }

    suspend fun sellFunds() {
        val transactions = transactionsRepo.find(
            TransactionFilter(
                status = TransactionStatus.PROCESSING,
                subwalletType = listOf(
                    SubwalletType.STOCK,
                    SubwalletType.BONDS,
                    SubwalletType.REAL_ESTATE,
                    SubwalletType.CRYPTOCURRENCY
                )
            )
        )

        for (liquidationTransaction in transactions) {
            val transaction = transactionsService.processTransaction(
                ProcessTransactionRequest(
                    amount = liquidationTransaction.amount,
                    idempotencyKey = liquidationTransaction.id,
                    originatorWalletId = liquidationTransaction.originatorWalletId,
                    originatorSubwalletType = liquidationTransaction.originatorSubwalletType,
                    type = TransactionType.TRANSFER_FROM_HOLD,
                )
            )

            when (transaction.status) {
                TransactionStatus.COMPLETED -> liquidationTransaction.updateStatus(
                    transactionsRepo,
                    newStatus = TransactionStatus.COMPLETED
                )

                else -> {
                    logger.error("Transaction ${liquidationTransaction.id} failed")
                    liquidationTransaction.reverse(ledgerService)
                    liquidationTransaction.updateStatus(transactionsRepo, newStatus = TransactionStatus.FAILED)
                }
            }
        }
    }
}