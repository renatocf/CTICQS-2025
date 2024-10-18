package core.errors

trait PartnerServiceError extends DigitalWalletError {
  def message: String
}

