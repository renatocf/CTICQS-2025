package digitalwallet.services

import digitalwallet.data.common.Logger
import digitalwallet.data.common.ProcessTransactionRequest
import digitalwallet.data.common.exceptions.DbError
import digitalwallet.data.common.exceptions.StatusTransitionNotAllowed
import digitalwallet.data.enums.TransactionStatus
import digitalwallet.repo.TransactionsRepo
import java.time.LocalDateTime

class TransactionsService(
    private val transactionsRepo: TransactionsRepo,
) {
    private val logger = Logger()

    fun processTransaction(processTransactionRequest: ProcessTransactionRequest) {
        val transaction = transactionsRepo.insert(processTransactionRequest).dto()

        try {
            transaction.validate()
            val postedAt = transaction.postToLedger()

            // actually, we should not complete transaction right way
            // for deposit/withdraw, we can complete right way (we understand funds were already moved)
            // for transfer, we have to call some kind of external service to actually move funds instantly.
            // Only after this we complete the transaction
            // for hold, the transaction will remain in processing state and will be completed only when we
            // invest/liquidate funds

            transaction.updateStatus(TransactionStatus.COMPLETED, at = postedAt)
        } catch (e: ValidationException) {
            val message = e.message.toString()
            logger.error(message)
            transaction.updateStatus(TransactionStatus.FAILED, statusReason = message) // might throw exception
        } catch (e: StatusTransitionNotAllowed) {
            val message = e.message.toString()
            logger.error(message)
            transaction.reversePostToLedger()
            transaction.updateStatus(TransactionStatus.FAILED, statusReason = message) // might throw exception
        } catch (e: DbError) {
            val message = e.message.toString()
            logger.error(message)
            transaction.reversePostToLedger()
            transaction.updateStatus(TransactionStatus.TRANSIENT_ERROR) // might throw exception
        }
    }

    fun processTransactions(processTransactionsRequest: List<ProcessTransactionRequest>) {
        for (transaction in processTransactionsRequest) {
            processTransaction(transaction)
        }
    }
}