package core.domain.enums

object SubwalletType extends Enumeration {
  type SubwalletType = Value
  val RealMoney, EmergencyFunds, Stock, Bonds, RealEstate, Cryptocurrency = Value
}
