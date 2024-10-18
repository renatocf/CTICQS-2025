package core.errors

sealed trait InvestmentServiceError extends DigitalWalletError {
  def message: String
}

case class CreateTransactionFailed(message: String) extends InvestmentServiceError

case class ProcessTransactionFailed(message: String) extends InvestmentServiceError

case class ExecuteTransactionFailed(message: String) extends InvestmentServiceError

case class InvestmentServiceInternalError(message: String) extends InvestmentServiceError