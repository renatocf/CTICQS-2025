package core.errors

sealed trait WalletsServiceError extends DigitalWalletError

case class InvestmentFailedError(message: String) extends WalletsServiceError
case class LiquidationFailedError(message: String) extends WalletsServiceError