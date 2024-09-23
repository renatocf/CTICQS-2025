package core.errors

sealed trait TransactionValidationError extends TransactionError

case class OriginatorSubwalletTypeValidationError(message: String) extends TransactionValidationError
case class InsufficientFundsValidationError(message: String) extends TransactionValidationError
case class WalletNotFound(message: String) extends TransactionValidationError
case class TransferBetweenSubwalletsValidationError(message: String) extends TransactionValidationError
case class TransferBetweenWalletsValidationError(message: String) extends TransactionValidationError
case class StatusTransitionNotAllowed(message: String) extends TransactionValidationError



