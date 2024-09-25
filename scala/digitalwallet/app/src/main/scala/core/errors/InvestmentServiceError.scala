package core.errors

trait InvestmentServiceError extends DigitalWalletError

case class ProcessTransactionFailed(message: String) extends InvestmentServiceError
case class InvestmentServiceInternalError(message: String) extends InvestmentServiceError