package core.domain.enums

object BalanceType extends Enumeration {
  type BalanceType = Value
  val Available, Holding, Internal = Value
}