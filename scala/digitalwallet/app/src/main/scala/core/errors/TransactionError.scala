package core.errors

trait TransactionError extends DigitalWalletError

case class MissingBeneficiaryWalletId(message: String) extends TransactionError
case class MissingBeneficiarySubwalletType(message: String) extends TransactionError
case class ExecutionFailed(message: String) extends TransactionError
