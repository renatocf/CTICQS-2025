package digitalwallet.core.services

import digitalwallet.adapters.Logger
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.InvestmentPolicy
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.TransactionFailed
import digitalwallet.ports.*
import java.math.BigDecimal

data class InvestmentMovementRequest(
    val amount: BigDecimal,
    val idempotencyKey: String,
    val walletId: String,
    val targetWalletId: String? = null,
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
            val processTransactionRequest =
                ProcessTransactionRequest(
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

    private suspend fun transferWithPolicy(
        request: InvestmentMovementRequest,
        originatorSubwalletType: SubwalletType,
    ) {
        for ((subwalletType, percentage) in request.investmentPolicy.allocationStrategy) {
            if (percentage > BigDecimal(0)) {
                val processTransactionRequest =
                    ProcessTransactionRequest(
                        amount = request.amount.multiply(percentage),
                        batchId = request.idempotencyKey,
                        idempotencyKey = "${request.idempotencyKey}_$subwalletType",
                        originatorWalletId = request.walletId,
                        originatorSubwalletType = originatorSubwalletType,
                        beneficiaryWalletId = request.targetWalletId,
                        beneficiarySubwalletType = subwalletType,
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
    }

    suspend fun buyFunds() {
        val transactions =
            transactionsRepo.find(
                TransactionFilter(
                    status = TransactionStatus.PROCESSING,
                    subwalletType = listOf(SubwalletType.REAL_MONEY),
                ),
            )

        for (investmentTransaction in transactions) {
            val wallet =
                walletRepo.findById(investmentTransaction.originatorWalletId)
                    ?: throw NoSuchElementException("Wallet ${investmentTransaction.originatorWalletId} not found")
            val investmentPolicy =
                investmentPolicyRepo.findById(wallet.policyId)
                    ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")
            val investmentWallet =
                walletRepo
                    .find(
                        WalletFilter(
                            customerId = wallet.customerId,
                            type = WalletType.INVESTMENT,
                        ),
                    ).firstOrNull() ?: throw NoSuchElementException("Wallet not found for customer ${wallet.customerId}")

            try {
                transferWithPolicy(
                    InvestmentMovementRequest(
                        amount = investmentTransaction.amount,
                        idempotencyKey = investmentTransaction.id,
                        walletId = investmentTransaction.originatorWalletId,
                        targetWalletId = investmentWallet.id,
                        investmentPolicy = investmentPolicy,
                    ),
                    SubwalletType.REAL_MONEY,
                )

                investmentTransaction.updateStatus(transactionsRepo, newStatus = TransactionStatus.COMPLETED)
            } catch (e: TransactionFailed) {
                val message = e.message.toString()
                logger.error(message)
                investmentTransaction.reverse(ledgerService)
                investmentTransaction.updateStatus(
                    transactionsRepo,
                    newStatus = TransactionStatus.FAILED,
                    statusReason = message,
                )
            }
        }
    }

    suspend fun sellFunds() {
        val transactions =
            transactionsRepo.find(
                TransactionFilter(
                    status = TransactionStatus.PROCESSING,
                    subwalletType =
                        listOf(
                            SubwalletType.STOCK,
                            SubwalletType.BONDS,
                            SubwalletType.REAL_ESTATE,
                            SubwalletType.CRYPTOCURRENCY,
                        ),
                ),
            )

        for (liquidationTransaction in transactions) {
            val wallet =
                walletRepo.findById(liquidationTransaction.originatorWalletId)
                    ?: throw NoSuchElementException("Wallet ${liquidationTransaction.originatorWalletId} not found")
            val realMoneyWallet =
                walletRepo
                    .find(
                        WalletFilter(
                            customerId = wallet.customerId,
                            type = WalletType.REAL_MONEY,
                        ),
                    ).firstOrNull() ?: throw NoSuchElementException("Wallet not found for customer ${wallet.customerId}")

            val transaction =
                transactionsService.processTransaction(
                    ProcessTransactionRequest(
                        amount = liquidationTransaction.amount,
                        idempotencyKey = liquidationTransaction.id,
                        originatorWalletId = liquidationTransaction.originatorWalletId,
                        originatorSubwalletType = liquidationTransaction.originatorSubwalletType,
                        beneficiaryWalletId = realMoneyWallet.id,
                        beneficiarySubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.TRANSFER_FROM_HOLD,
                    ),
                )

            when (transaction.status) {
                TransactionStatus.COMPLETED ->
                    liquidationTransaction.updateStatus(
                        transactionsRepo,
                        newStatus = TransactionStatus.COMPLETED,
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
