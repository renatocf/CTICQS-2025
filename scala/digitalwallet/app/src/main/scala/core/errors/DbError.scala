package core.errors

sealed trait DbError extends DigitalWalletError

sealed trait TransactionDbError extends DbError
case class TransactionNotFound(message: String) extends TransactionDbError

sealed trait WalletsDbError extends DbError
case class WalletNotFound(message: String) extends WalletsDbError