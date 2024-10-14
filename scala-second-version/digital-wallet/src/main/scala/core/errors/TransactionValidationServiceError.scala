package core.errors

trait TransactionValidationError extends DigitalWalletError {
  def message: String
}

case class OriginatorSubwalletTypeValidationError(message: String) extends TransactionValidationError
case class InsufficientFundsValidationError(message: String) extends TransactionValidationError
case class TransferBetweenSubwalletsValidationError(message: String) extends TransactionValidationError
case class TransferBetweenWalletsValidationError(message: String) extends TransactionValidationError
case class TransactionServiceInvalid(message: String) extends TransactionValidationError
case class TransactionValidationFailed(message: String) extends TransactionValidationError