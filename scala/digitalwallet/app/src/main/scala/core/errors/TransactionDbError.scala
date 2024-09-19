package core.errors

sealed trait TransactionDbError {
  def message: String
}

case class StatusTransitionNotAllowed(message: String) extends TransactionDbError
case class TransactionNotFound(message: String) extends TransactionDbError
