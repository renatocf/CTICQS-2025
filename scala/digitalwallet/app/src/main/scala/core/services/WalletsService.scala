package core.services

import core.domain.entities.Wallet
import core.domain.enums.{BalanceType, SubwalletType, WalletType}
import core.domain.model.LedgerQuery
import ports.WalletsDatabase

class WalletsService(walletsRepo: WalletsDatabase, ledgerService: LedgerService) {
    def getAvailableBalance(wallet: Wallet): BigDecimal = {
      val ledgerQuery = wallet.walletType match {
        case WalletType.RealMoney =>
          List(
            LedgerQuery(
              subwalletType = SubwalletType.RealMoney,
              balanceType = BalanceType.Available
            )
          )
        case WalletType.Investment =>
          List(
            LedgerQuery(
              subwalletType = SubwalletType.Bonds,
              balanceType = BalanceType.Available,
            ),
            LedgerQuery(
              subwalletType = SubwalletType.Stock,
              balanceType = BalanceType.Available,
            ),
            LedgerQuery(
              subwalletType = SubwalletType.RealEstate,
              balanceType = BalanceType.Available,
            ),
            LedgerQuery(
              subwalletType = SubwalletType.Cryptocurrency,
              balanceType = BalanceType.Available,
            )
          )
        case WalletType.EmergencyFunds =>
          List(
            LedgerQuery(
              subwalletType = SubwalletType.EmergencyFunds,
              balanceType = BalanceType.Available
            )
          )
      }

      ledgerService.getBalance(wallet.id, ledgerQuery)
    }
}
