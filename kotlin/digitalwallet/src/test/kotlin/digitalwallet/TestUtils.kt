package digitalwallet

import digitalwallet.adapters.InvestmentPolicyInMemoryDatabase
import digitalwallet.adapters.LedgerInMemoryDatabase
import digitalwallet.adapters.TransactionsInMemoryDatabase
import digitalwallet.adapters.WalletsInMemoryDatabase
import digitalwallet.core.domain.entities.InvestmentPolicy
import digitalwallet.core.domain.entities.Transaction
import digitalwallet.core.domain.entities.Wallet
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.TransactionMetadata
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

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

fun insertTransactionInMemory(
    db: TransactionsInMemoryDatabase,
    id: String = UUID.randomUUID().toString(),
    batchId: String? = null,
    amount: BigDecimal = BigDecimal(100),
    idempotencyKey: String = "idempotencyKey",
    originatorWalletId: String = "originatorWalletId",
    originatorSubwalletType: SubwalletType = SubwalletType.REAL_MONEY,
    beneficiaryWalletId: String? = null,
    beneficiarySubwalletType: SubwalletType? = null,
    type: TransactionType = TransactionType.DEPOSIT,
    insertedAt: LocalDateTime = LocalDateTime.now(),
    status: TransactionStatus = TransactionStatus.PROCESSING,
    metadata: TransactionMetadata? = null,
): Transaction {
    val transaction =
        Transaction(
            id = id,
            amount = amount,
            batchId = batchId,
            idempotencyKey = idempotencyKey,
            originatorWalletId = originatorWalletId,
            originatorSubwalletType = originatorSubwalletType,
            beneficiaryWalletId = beneficiaryWalletId,
            beneficiarySubwalletType = beneficiarySubwalletType,
            type = type,
            insertedAt = insertedAt,
            status = status,
            metadata = metadata,
        )

    db.transactions[id] = transaction

    return transaction
}
