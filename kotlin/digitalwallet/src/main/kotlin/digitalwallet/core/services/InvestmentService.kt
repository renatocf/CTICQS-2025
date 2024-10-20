package digitalwallet.core.services

import digitalwallet.adapters.Logger
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.InvestmentPolicy
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.PartnerException
import digitalwallet.core.exceptions.TransactionFailed
import digitalwallet.core.exceptions.ValidationException
import digitalwallet.ports.*
import java.math.BigDecimal

data class InvestmentMovementRequest(
    val amount: BigDecimal,
    val idempotencyKey: String,
    val walletId: String,
    val targetWalletId: String? = null,
    val investmentPolicy: InvestmentPolicy,
    val transactionType: TransactionType,
)

class InvestmentService(
    private val transactionsRepo: TransactionsDatabase,
    private val walletRepo: WalletsDatabase,
    private val investmentPolicyRepo: InvestmentPolicyDatabase,
    private val transactionsService: TransactionsService,
    private val ledgerService: LedgerService,
) {
    private val logger = Logger()

    suspend fun executeMovementWithInvestmentPolicy(
        request: InvestmentMovementRequest,
        originatorSubwalletType: SubwalletType? = null,
    ) {
        for ((subwalletType, percentage) in request.investmentPolicy.allocationStrategy) {
            val processTransactionRequest: ProcessTransactionRequest =
                when (request.transactionType) {
                    TransactionType.HOLD -> buildHoldRequest(request, subwalletType, percentage)
                    TransactionType.TRANSFER_FROM_HOLD ->
                        buildTransferFromHoldRequest(
                            request,
                            originatorSubwalletType,
                            subwalletType,
                            percentage,
                        )
                    else -> throw IllegalArgumentException("Unsupported transaction type: ${request.transactionType}")
                }

            try {
                transactionsService.processTransaction(processTransactionRequest)
            } catch (e: ValidationException) {
                transactionsService.handleException(e, TransactionStatus.FAILED, request.idempotencyKey)
                // problem: we cannot ensure atomicity as we might have executed transfers with the partner
                // at this point. We'd have to call the partner again to reverse the transfers already executed
                // in this batch and then reverse the journal entries posted to ledger, which sounds not the
                // best design...
                throw TransactionFailed("holdWithPolicy failed for ${request.idempotencyKey}")
            } catch (e: PartnerException) {
                // this error can be retried; let's just ignore it
                transactionsService.handleException(e, TransactionStatus.TRANSIENT_ERROR, request.idempotencyKey)
            }
        }
    }

    private suspend fun buildHoldRequest(
        request: InvestmentMovementRequest,
        subwalletType: SubwalletType,
        percentage: BigDecimal,
    ): ProcessTransactionRequest =
        ProcessTransactionRequest(
            amount = request.amount * percentage,
            batchId = request.idempotencyKey,
            idempotencyKey = "${request.idempotencyKey}_$subwalletType}",
            originatorWalletId = request.walletId,
            originatorSubwalletType = subwalletType,
            type = TransactionType.HOLD,
        )

    private suspend fun buildTransferFromHoldRequest(
        request: InvestmentMovementRequest,
        originatorSubwalletType: SubwalletType?,
        beneficiarySubwalletType: SubwalletType,
        percentage: BigDecimal,
    ): ProcessTransactionRequest {
        val subwalletType = originatorSubwalletType ?: throw IllegalArgumentException("originatorSubwalletType required but not provided.")

        return ProcessTransactionRequest(
            amount = request.amount.multiply(percentage),
            batchId = request.idempotencyKey,
            idempotencyKey = "${request.idempotencyKey}_$beneficiarySubwalletType",
            originatorWalletId = request.walletId,
            originatorSubwalletType = subwalletType,
            beneficiaryWalletId = request.targetWalletId,
            beneficiarySubwalletType = beneficiarySubwalletType,
            type = TransactionType.TRANSFER_FROM_HOLD,
        )
    }

//    private suspend fun transferWithPolicy(
//        request: InvestmentMovementRequest,
//        originatorSubwalletType: SubwalletType,
//    ) {
//        for ((subwalletType, percentage) in request.investmentPolicy.allocationStrategy) {
//            if (percentage > BigDecimal(0)) {
//                val processTransactionRequest =
//                    ProcessTransactionRequest(
//                        amount = request.amount.multiply(percentage),
//                        batchId = request.idempotencyKey,
//                        idempotencyKey = "${request.idempotencyKey}_$subwalletType",
//                        originatorWalletId = request.walletId,
//                        originatorSubwalletType = originatorSubwalletType,
//                        beneficiaryWalletId = request.targetWalletId,
//                        beneficiarySubwalletType = subwalletType,
//                        type = TransactionType.TRANSFER_FROM_HOLD,
//                    )
//
//                try {
//                    transactionsService.processTransaction(processTransactionRequest)
//                } catch (e: ValidationException) {
//                    transactionsService.handleException(e, TransactionStatus.FAILED, request.idempotencyKey)
//                    // problem: we cannot ensure atomicity as we might have executed transfers with the partner
//                    // at this point. We'd have to call the partner again to reverse the transfers already executed
//                    // in this batch and then reverse the journal entries posted to ledger.
//                    throw TransactionFailed("holdWithPolicy failed for ${request.idempotencyKey}")
//                } catch (e: PartnerException) {
//                    // this error can be retried; let's just ignore it
//                    transactionsService.handleException(e, TransactionStatus.TRANSIENT_ERROR, request.idempotencyKey)
//                }
//            }
//        }
//    }

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
                executeMovementWithInvestmentPolicy(
                    InvestmentMovementRequest(
                        amount = investmentTransaction.amount,
                        idempotencyKey = investmentTransaction.id,
                        walletId = investmentTransaction.originatorWalletId,
                        targetWalletId = investmentWallet.id,
                        investmentPolicy = investmentPolicy,
                        transactionType = TransactionType.TRANSFER_FROM_HOLD,
                    ),
                    SubwalletType.REAL_MONEY,
                )

                val transientErrorTransactions =
                    transactionsRepo.find(
                        TransactionFilter(
                            batchId = investmentTransaction.id,
                            status = TransactionStatus.TRANSIENT_ERROR,
                        ),
                    )

                if (transientErrorTransactions.isEmpty()) {
                    investmentTransaction.updateStatus(transactionsRepo, newStatus = TransactionStatus.COMPLETED)
                }
            } catch (e: TransactionFailed) {
                val message = e.message.toString()
                logger.error(message)
                // validations failed for some transaction in the batch, but other transactions
                // may have succeeded before and their funds are invested now!
                // To solve this, we'd have to reverse the investment transaction partially or
                // use manual remediation...
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
