package core.errors

trait PartnerServiceError extends DigitalWalletError {
  def message: String
}

case class PartnerServiceInternalError(message: String) extends PartnerServiceError

