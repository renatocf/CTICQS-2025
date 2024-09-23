package core.errors

trait WalletsServiceError extends DigitalWalletError

case class InvestmentFailedError(message: String) extends WalletsServiceError
