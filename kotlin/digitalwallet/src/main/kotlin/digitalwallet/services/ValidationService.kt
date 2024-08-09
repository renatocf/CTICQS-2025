package digitalwallet.services

import digitalwallet.data.common.exceptions.HoldNotAllowed
import digitalwallet.data.common.exceptions.TransferNotAllowed
import digitalwallet.data.enums.SubwalletType
import digitalwallet.data.models.Transaction
import digitalwallet.repo.WalletsRepo

open class ValidationException(message: String) : Exception(message)

class ExternalTransactionValidationException(message: String) : ValidationException(message)
class InsufficientFundsException(message: String) : ValidationException(message)
class WalletMinimumCommitmentAmountException(message: String) : ValidationException(message)

class ValidationService(
    private val walletsRepo: WalletsRepo,
) {
//    fun validateTransaction(transaction: Transaction) {
//        when (transaction.type) {
//            TransactionType.DEPOSIT -> {
//                validateExternalTransaction(transaction)
//            }
//            TransactionType.WITHDRAW -> {
//                validateExternalTransaction(transaction)
//                validateBalance(transaction)
//            }
//            TransactionType.HOLD -> {
//                validateHold(transaction)
//                validateBalance(transaction)
//            }
//            TransactionType.TRANSFER -> {
//                validateTransfer(transaction)
//                validateBalance(transaction)
//            }
//        }
//    }

    fun validateExternalTransaction(transaction: Transaction) {
        if (transaction.originatorSubwalletType != SubwalletType.REAL_MONEY) {
            throw ExternalTransactionValidationException("External transaction not allowed on ${transaction.originatorSubwalletType} type")
        }
    }

    fun validateBalance(transaction: Transaction) {
        val walletId = transaction.originatorWalletId
        val wallet = walletsRepo.findById(walletId)?.dto() ?: throw NoSuchElementException("Wallet $walletId not found")

        val balance = wallet.getAvailableBalance()

        if (transaction.amount > balance) {
            throw InsufficientFundsException("Wallet has no sufficient funds ")
        }
    }

//    private fun validateHold(transaction: Transaction) {
//        if (transaction.originatorSubwalletType !in listOf(SubwalletType.REAL_MONEY, SubwalletType.INVESTMENT)) {
//            throw HoldNotAllowed("Hold not allowed on ${transaction.originatorSubwalletType} type")
//        }
//    }
//
//    private fun validateTransfer(transaction: Transaction) {
//        val originatorSubwalletType = transaction.originatorSubwalletType
//        val beneficiarySubwalletType = transaction.beneficiarySubwalletType
//
//        val validTransferPairs = setOf(
//            SubwalletType.REAL_MONEY to SubwalletType.EMERGENCY_FUND,
//            SubwalletType.EMERGENCY_FUND to SubwalletType.REAL_MONEY
//        )
//
//        if ((originatorSubwalletType to beneficiarySubwalletType) !in validTransferPairs) {
//            throw TransferNotAllowed("Transfer not allowed between $originatorSubwalletType and $beneficiarySubwalletType types")
//        }
//    }
}