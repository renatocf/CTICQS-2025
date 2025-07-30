package core.domain.enums

object TransactionStatus extends Enumeration {
  type TransactionStatus = Value
  val Creating, Processing, Failed, TransientError, Completed = Value
}
