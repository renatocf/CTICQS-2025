package digitalwallet.data.common.exceptions

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException

open class ValidationException(message: String) : Exception(message)

class ExternalTransactionValidationException(message: String) : ValidationException(message)
class InsufficientFundsException(message: String) : ValidationException(message)
class WalletMinimumCommitmentAmountException(message: String) : ValidationException(message)
class HoldNotAllowed(message: String) : ValidationException(message)