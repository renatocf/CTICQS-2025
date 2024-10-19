package core.errors

trait TransactionServiceError extends DigitalWalletError {
  def message: String
}

case class ExecutionError(message: String) extends TransactionServiceError
case class ProcessError(message: String) extends TransactionServiceError
case class CreationError(message: String) extends TransactionServiceError
case class TransactionServiceInternalError(message: String) extends TransactionServiceError