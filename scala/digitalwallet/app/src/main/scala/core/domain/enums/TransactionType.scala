package core.domain.enums

object TransactionType extends Enumeration {
  type TransactionType = Value
  val Deposit, Withdraw, Transfer, Hold, TransferFromHold = Value
}