package core.errors

sealed trait InvestmentServiceError extends DigitalWalletError {
  def message: String
}

case class ProcessTransactionFailed(message: String) extends InvestmentServiceError
case class InvestmentServiceInternalError(message: String) extends InvestmentServiceError