package digitalwallet

import digitalwallet.adapters.InvestmentPolicyInMemoryDatabase
import digitalwallet.adapters.LedgerInMemoryDatabase
import digitalwallet.adapters.TransactionsInMemoryDatabase
import digitalwallet.adapters.WalletsInMemoryDatabase
import digitalwallet.core.domain.entities.InvestmentPolicy
import digitalwallet.core.domain.entities.Wallet
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.WalletType
import java.math.BigDecimal
import java.time.LocalDateTime

fun clearInMemoryDatabase(
    transactionsDatabaseInMemory: TransactionsInMemoryDatabase? = null,
    walletsDatabaseInMemory: WalletsInMemoryDatabase? = null,
    ledgerDatabaseInMemory: LedgerInMemoryDatabase? = null,
    investmentPolicyDatabaseInMemory: InvestmentPolicyInMemoryDatabase? = null,
) {
    transactionsDatabaseInMemory?.clear()
    walletsDatabaseInMemory?.clear()
    ledgerDatabaseInMemory?.clear()
    investmentPolicyDatabaseInMemory?.clear()
}

fun insertWalletInMemory(
    db: WalletsInMemoryDatabase,
    id: String,
    customerId: String,
    type: WalletType,
    policyId: String,
): Wallet {
    val wallet =
        Wallet(
            id = id,
            customerId = customerId,
            type = type,
            policyId = policyId,
            insertedAt = LocalDateTime.now(),
        )

    db.wallets[id] = wallet

    return wallet
}

fun insertInvestmentPolicyInMemory(
    db: InvestmentPolicyInMemoryDatabase,
    id: String,
    allocationStrategy: MutableMap<SubwalletType, BigDecimal>,
): InvestmentPolicy {
    val investmentPolicy =
        InvestmentPolicy(
            id = id,
            allocationStrategy = allocationStrategy,
        )

    db.investmentPolicies[id] = investmentPolicy

    return investmentPolicy
}
