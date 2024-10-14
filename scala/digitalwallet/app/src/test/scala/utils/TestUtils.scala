package utils

import adapters.{InvestmentPolicyInMemoryDatabase, LedgerInMemoryDatabase, TransactionInMemoryDatabase, WalletsInMemoryDatabase}
import core.domain.entities.{InvestmentPolicy, Transaction, Wallet}
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.enums.TransactionType.TransactionType
import core.domain.enums.WalletType.WalletType
import core.domain.enums.{SubwalletType, TransactionStatus, TransactionType}
import core.domain.model.TransactionMetadata

import java.time.LocalDateTime
import java.util.UUID

class TestUtils {
  def clearInMemoryDatabase(
   transactionsDatabaseInMemory: Option[TransactionInMemoryDatabase] = None,
   walletsDatabaseInMemory: Option[WalletsInMemoryDatabase] = None,
   ledgerDatabaseInMemory: Option[LedgerInMemoryDatabase] = None,
   investmentPolicyDatabaseInMemory: Option[InvestmentPolicyInMemoryDatabase] = None
  ): Unit = {
    transactionsDatabaseInMemory.foreach(_.clear())
    walletsDatabaseInMemory.foreach(_.clear())
    ledgerDatabaseInMemory.foreach(_.clear())
    investmentPolicyDatabaseInMemory.foreach(_.clear())
  }

  def insertWalletInMemory(
    db: WalletsInMemoryDatabase,
    id: String,
    customerId: String,
    walletType: WalletType,
    policyId: String
  ): Wallet = {
    val wallet = Wallet(
      id = id,
      customerId = customerId,
      walletType = walletType,
      policyId = policyId,
      insertedAt = LocalDateTime.now()
    )

    db.wallets(id) = wallet
    wallet
  }

  def insertInvestmentPolicyInMemory(
    db: InvestmentPolicyInMemoryDatabase,
    id: String,
    allocationStrategy: Map[SubwalletType, BigDecimal]
  ): InvestmentPolicy = {
    val investmentPolicy = InvestmentPolicy(
      id = id,
      allocationStrategy = allocationStrategy
    )

    db.investmentPolicies(id) = investmentPolicy
    investmentPolicy
  }

  def insertTransactionInMemory(
   db: TransactionInMemoryDatabase,
   id: String = UUID.randomUUID().toString,
   batchId: Option[String] = None,
   amount: BigDecimal = BigDecimal(100),
   idempotencyKey: String = "idempotencyKey",
   originatorWalletId: String = "originatorWalletId",
   originatorSubwalletType: SubwalletType = SubwalletType.RealMoney,
   beneficiaryWalletId: Option[String] = None,
   beneficiarySubwalletType: Option[SubwalletType] = None,
   transactionType: TransactionType = TransactionType.Deposit,
   insertedAt: LocalDateTime = LocalDateTime.now(),
   status: TransactionStatus = TransactionStatus.Processing,
   metadata: Option[TransactionMetadata] = None
 ): Transaction = {
    val transaction = Transaction(
      id = id,
      amount = amount,
      batchId = batchId,
      idempotencyKey = idempotencyKey,
      originatorWalletId = originatorWalletId,
      originatorSubwalletType = originatorSubwalletType,
      beneficiaryWalletId = beneficiaryWalletId,
      beneficiarySubwalletType = beneficiarySubwalletType,
      transactionType = transactionType,
      insertedAt = insertedAt,
      status = status,
      metadata = metadata
    )

    db.transactions(id) = transaction
    transaction
  }
}