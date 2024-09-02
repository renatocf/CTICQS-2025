package digitalwallet.core.services

import digitalwallet.adapters.Logger
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.TransactionFailed
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.models.InvestmentPolicy
import digitalwallet.ports.InvestmentPolicyDatabase
import digitalwallet.ports.TransactionFilter
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import java.math.BigDecimal

data class InvestmentMovementRequest(
    val amount: BigDecimal,
    val idempotencyKey: String,
    val walletId: String,
    val investmentPolicy: InvestmentPolicy,
)

class InvestmentService(
    private val transactionsRepo: TransactionsDatabase,
    private val walletRepo: WalletsDatabase,
    private val investmentPolicyRepo: InvestmentPolicyDatabase,
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

            if (transaction.status != digitalwallet.core.domain.enums.TransactionStatus.PROCESSING) {
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

            if (transaction.status != digitalwallet.core.domain.enums.TransactionStatus.COMPLETED) {
                logger.error("Investment movement failed for request ${request.idempotencyKey}")
                transactionsService.reverseAndFailTransactionsBatch(request.idempotencyKey)
                throw TransactionFailed("Transaction ${transaction.id} failed")
            }
        }
    }

    suspend fun buyFunds() {
        val transactions = transactionsRepo.find(
            TransactionFilter(
                status = digitalwallet.core.domain.enums.TransactionStatus.PROCESSING,
                subwalletType = listOf(digitalwallet.core.domain.enums.SubwalletType.REAL_MONEY),
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

                investmentTransaction.updateStatus(transactionsRepo, newStatus = digitalwallet.core.domain.enums.TransactionStatus.COMPLETED)
            } catch (e: TransactionFailed) {
                val message = e.message.toString()
                logger.error(message)
                investmentTransaction.reverse(ledgerService)
                investmentTransaction.updateStatus(
                    transactionsRepo,
                    newStatus = digitalwallet.core.domain.enums.TransactionStatus.FAILED,
                    statusReason = message
                )
            }
        }
    }

    suspend fun sellFunds() {
        val transactions = transactionsRepo.find(
            TransactionFilter(
                status = digitalwallet.core.domain.enums.TransactionStatus.PROCESSING,
                subwalletType = listOf(
                    digitalwallet.core.domain.enums.SubwalletType.STOCK,
                    digitalwallet.core.domain.enums.SubwalletType.BONDS,
                    digitalwallet.core.domain.enums.SubwalletType.REAL_ESTATE,
                    digitalwallet.core.domain.enums.SubwalletType.CRYPTOCURRENCY
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
                digitalwallet.core.domain.enums.TransactionStatus.COMPLETED -> liquidationTransaction.updateStatus(
                    transactionsRepo,
                    newStatus = digitalwallet.core.domain.enums.TransactionStatus.COMPLETED
                )

                else -> {
                    logger.error("Transaction ${liquidationTransaction.id} failed")
                    liquidationTransaction.reverse(ledgerService)
                    liquidationTransaction.updateStatus(transactionsRepo, newStatus = digitalwallet.core.domain.enums.TransactionStatus.FAILED)
                }
            }
        }
    }
}