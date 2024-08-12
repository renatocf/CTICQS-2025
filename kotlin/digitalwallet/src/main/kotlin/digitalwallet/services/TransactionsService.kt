package digitalwallet.services

import digitalwallet.data.common.Logger
import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.DbError
import digitalwallet.data.common.exceptions.StatusTransitionNotAllowed
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.data.models.Transaction
import digitalwallet.repo.TransactionFilter
import digitalwallet.repo.TransactionsRepo
import digitalwallet.repo.WalletsRepo
import java.time.LocalDateTime

class TransactionsService(
    private val transactionsRepo: TransactionsRepo,
    private val walletsRepo: WalletsRepo,
    private val ledgerService: LedgerService,
    private val partnerService: PartnerService,
) {
    private val logger = Logger()

    suspend fun processTransaction(processTransactionRequest: ProcessTransactionRequest) : Transaction {
        val transaction = transactionsRepo.insert(processTransactionRequest).dto()

        try {
            transaction.validate(walletsRepo, ledgerService)
            transaction.process(transactionsRepo, ledgerService, partnerService)
            // actually, we should not complete transaction right way
            // for deposit/withdraw, we can complete right way (we understand funds were already moved)
            // for transfer, we have to call some kind of external service to actually move funds instantly.
            // Only after this we complete the transaction
            // for hold, the transaction will remain in processing state and will be completed only when we
            // invest/liquidate funds

        } catch (e: ValidationException) {
            val message = e.message.toString()
            logger.error(message)
            transaction.updateStatus(transactionsRepo, TransactionStatus.FAILED, statusReason = message) // might throw exception
        } catch (e: StatusTransitionNotAllowed) {
            val message = e.message.toString()
            logger.error(message)
            transaction.reverse(ledgerService)
            transaction.updateStatus(transactionsRepo, TransactionStatus.FAILED, statusReason = message) // might throw exception
        } catch (e: DbError) {
            val message = e.message.toString()
            logger.error(message)
            transaction.reverse(ledgerService)
            transaction.updateStatus(transactionsRepo, TransactionStatus.TRANSIENT_ERROR) // might throw exception
        }

        return transaction
    }

    suspend fun processTransactions(processTransactionsRequest: List<ProcessTransactionRequest>) {
        for (transaction in processTransactionsRequest) {
            processTransaction(transaction)
        }
    }

    fun reverseAndFailTransactionsBatch(batchId: String, failureReason: String? = null) {
        val transactions = transactionsRepo.find(TransactionFilter(
            batchId = batchId
        ))

        for (transaction in transactions) {
            transaction.reverse(ledgerService)
            transaction.updateStatus(transactionsRepo, TransactionStatus.FAILED, statusReason = failureReason)
        }
    }
}