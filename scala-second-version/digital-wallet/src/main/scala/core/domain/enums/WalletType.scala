package core.domain.enums

object WalletType extends Enumeration {
  type WalletType = Value
  val RealMoney, Investment, EmergencyFunds = Value
}