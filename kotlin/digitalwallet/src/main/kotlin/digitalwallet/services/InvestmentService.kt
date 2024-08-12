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

data class ProcessBatchWithInvestmentPolicy(
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

    suspend fun processHoldBatchWithInvestmentPolicy(request: ProcessBatchWithInvestmentPolicy) {
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
                transactionsService.reverseAndFailTransactionsBatch(request.idempotencyKey)
                throw TransactionFailed("Transaction ${transaction.id} failed")
            }
        }
    }

    suspend fun processTransferFromHoldBatchWithInvestmentPolicy(request: ProcessBatchWithInvestmentPolicy) {
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
            val wallet = walletRepo.findById(investmentTransaction.originatorWalletId)?.dto()
                ?: throw NoSuchElementException("Wallet ${investmentTransaction.originatorWalletId} not found")
            val investmentPolicy = investmentPolicyRepo.findById(wallet.policyId)?.dto()
                ?: throw NoSuchElementException("Policy ${wallet.policyId} not found")

            try {
                processTransferFromHoldBatchWithInvestmentPolicy(
                    ProcessBatchWithInvestmentPolicy(
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