package core.errors

sealed trait TransactionValidationError {
  def message: String
}

case class OriginatorSubwalletTypeValidationError(message: String) extends TransactionValidationError
case class InsufficientFundsValidationError(message: String) extends TransactionValidationError
case class WalletNotFound(message: String) extends TransactionValidationError
case class TransferBetweenSubwalletsValidationError(message: String) extends TransactionValidationError
case class TransferBetweenWalletsValidationError(message: String) extends TransactionValidationError
case class MissingBeneficiaryWalletId(message: String) extends TransactionValidationError
case class MissingBeneficiarySubwalletType(message: String) extends TransactionValidationError



