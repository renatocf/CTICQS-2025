package core.errors

trait DbError extends DigitalWalletError

case class TransactionDbError(message: String) extends DbError