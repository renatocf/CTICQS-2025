package digitalwallet.core.services

import digitalwallet.adapters.Logger
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.domain.models.Transaction
import digitalwallet.ports.TransactionFilter
import digitalwallet.ports.TransactionsDatabase
import digitalwallet.ports.WalletsDatabase
import jakarta.inject.Singleton

@Singleton
class TransactionsService(
    private val transactionsRepo: TransactionsDatabase,
    private val walletsRepo: WalletsDatabase,
    private val ledgerService: LedgerService,
    private val partnerService: PartnerService,
) {
    private val logger = Logger()

    suspend fun processTransaction(request: ProcessTransactionRequest): Transaction {
        val transaction = transactionsRepo.insert(request)
        transaction.validate(walletsRepo, ledgerService)
        transaction.process(transactionsRepo, ledgerService, partnerService)

        return transaction
    }

    fun handleException(
        e: Exception,
        status: TransactionStatus,
        idempotencyKey: String,
    ): Transaction? {
        val message = e.message.toString()
        logger.error(message)

        val transaction = transactionsRepo.find(TransactionFilter(idempotencyKey = idempotencyKey)).firstOrNull()
        transaction?.updateStatus(transactionsRepo, status)

        return transaction
    }

    fun reverseAndFailTransactionsBatch(batchId: String) {
        val transactions =
            transactionsRepo.find(
                TransactionFilter(
                    batchId = batchId,
                ),
            )

        for (transaction in transactions) {
            transaction.reverse(ledgerService)
            transaction.updateStatus(
                transactionsRepo,
                TransactionStatus.FAILED,
            )
        }
    }
}
