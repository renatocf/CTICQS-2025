package digitalwallet.data.common

import digitalwallet.data.enums.BalanceType
import digitalwallet.data.enums.SubwalletType

data class BalanceConfig(
    val subwalletType: SubwalletType,
    val balanceType: BalanceType,
) {
    companion object {
        fun availableRealMoney(): Array<BalanceConfig> {
            return arrayOf(BalanceConfig(
                subwalletType = SubwalletType.REAL_MONEY,
                balanceType = BalanceType.AVAILABLE,
              )
            )
        }

        fun availableInvestment(): Array<BalanceConfig> {
            return arrayOf(
                BalanceConfig(
                    subwalletType = SubwalletType.BONDS,
                    balanceType = BalanceType.AVAILABLE,
                ),
                BalanceConfig(
                    subwalletType = SubwalletType.STOCK,
                    balanceType = BalanceType.AVAILABLE,
                ),
                BalanceConfig(
                    subwalletType = SubwalletType.REAL_ESTATE,
                    balanceType = BalanceType.AVAILABLE,
                ),
                BalanceConfig(
                    subwalletType = SubwalletType.CRYPTOCURRENCY,
                    balanceType = BalanceType.AVAILABLE,
                )
            )
        }

        fun availableEmergencyFunds(): Array<BalanceConfig> {
           return arrayOf(BalanceConfig(
                subwalletType = SubwalletType.EMERGENCY_FUND,
                balanceType = BalanceType.AVAILABLE,
              )
            )
        }
    }
}