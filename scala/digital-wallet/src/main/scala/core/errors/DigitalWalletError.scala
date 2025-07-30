package core.errors

trait DigitalWalletError

case class MissingBeneficiaryWalletId(message: String) extends DigitalWalletError
case class MissingBeneficiarySubwalletType(message: String) extends DigitalWalletError

