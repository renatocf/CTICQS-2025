package digitalwallet.core.exceptions

open class ValidationException(
    message: String,
) : Exception(message)

class ExternalTransactionValidationException(
    message: String,
) : ValidationException(message)

class InsufficientFundsException(
    message: String,
) : ValidationException(message)

class TransferNotAllowed(
    message: String,
) : ValidationException(message)

class HoldNotAllowed(
    message: String,
) : ValidationException(message)

class TransferFromHoldNotAllowed(
    message: String,
) : ValidationException(message)
