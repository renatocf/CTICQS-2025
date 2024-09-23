package digitalwallet.core.services

import digitalwallet.adapters.Logger
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.domain.models.Transaction
import digitalwallet.core.exceptions.DbError
import digitalwallet.core.exceptions.PartnerException
import digitalwallet.core.exceptions.ValidationException
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

        try {
            transaction.validate(walletsRepo, ledgerService)
            transaction.process(transactionsRepo, ledgerService, partnerService)
        } catch (e: ValidationException) {
            val message = e.message.toString()
            logger.error(message)
            transaction.updateStatus(transactionsRepo, TransactionStatus.FAILED, statusReason = message)
        } catch (e: PartnerException) {
            val message = e.message.toString()
            logger.error(message)
            transaction.updateStatus(transactionsRepo, TransactionStatus.TRANSIENT_ERROR, statusReason = message)
        } catch (e: DbError) {
            val message = e.message.toString()
            logger.error(message)
            transaction.updateStatus(transactionsRepo, TransactionStatus.TRANSIENT_ERROR)
        }

        return transaction
    }

    fun reverseAndFailTransactionsBatch(
        batchId: String,
        failureReason: String? = null,
    ) {
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
                statusReason = failureReason,
            )
        }
    }
}
