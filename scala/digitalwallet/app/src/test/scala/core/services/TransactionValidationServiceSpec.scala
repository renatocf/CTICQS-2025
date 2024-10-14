package core.services

import adapters.{InvestmentPolicyInMemoryDatabase, WalletsInMemoryDatabase}
import core.domain.entities.Transaction
import core.domain.enums.{SubwalletType, TransactionStatus, TransactionType, WalletType}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.TestUtils

import java.time.LocalDateTime
import java.util.UUID

class TransactionValidationServiceSpec extends AnyFlatSpec with BeforeAndAfterEach with Matchers {

  val walletsRepo = new WalletsInMemoryDatabase()
  val investmentPolicyRepo = new InvestmentPolicyInMemoryDatabase()

  val walletsServiceMock: WalletsService = mock[WalletsService](classOf[WalletsService])
  val ledgerServiceMock: LedgerService = mock[LedgerService](classOf[LedgerService])

  val validationService = new TransactionValidationService(walletsRepo, walletsServiceMock, ledgerServiceMock)

  val utils = new TestUtils()

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(ledgerServiceMock.postJournalEntries(any())) thenReturn LocalDateTime.now()
    when(ledgerServiceMock.getBalance(any(), any())) thenReturn BigDecimal(100)

    val investmentPolicy = utils.insertInvestmentPolicyInMemory(
      db = investmentPolicyRepo,
      id = "policyId",
      allocationStrategy = Map(
        SubwalletType.RealEstate -> BigDecimal(0.4),
        SubwalletType.Cryptocurrency -> BigDecimal(0.1),
        SubwalletType.Bonds -> BigDecimal(0.1),
        SubwalletType.Stock -> BigDecimal(0.4)
      )
    )

    val customerId = "cust_123"

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "realMoneyWalletId",
      customerId = customerId,
      walletType = WalletType.RealMoney,
      policyId = investmentPolicy.id
    )

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "investmentWalletId",
      customerId = customerId,
      walletType = WalletType.Investment,
      policyId = investmentPolicy.id
    )

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "emergencyFundsWalletId",
      customerId = customerId,
      walletType = WalletType.EmergencyFunds,
      policyId = investmentPolicy.id
    )
  }

  override def afterEach(): Unit = {
    clearInvocations(ledgerServiceMock, walletsServiceMock)

    utils.clearInMemoryDatabase(
      walletsDatabaseInMemory = Some(walletsRepo),
      investmentPolicyDatabaseInMemory = Some(investmentPolicyRepo)
    )

    super.afterEach()
  }

  behavior of "TransactionValidationService"

  it should "validate a deposit transaction" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Deposit,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "wallet1",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = validationService.validateTransaction(transaction)
    result shouldBe Right(())
  }

//  it should "fail a deposit transaction with invalid subwallet type" in {
//    val transaction = Transaction(
//      transactionType = TransactionType.Deposit,
//      originatorSubwalletType = SubwalletType.Investment,
//      amount = BigDecimal(100),
//      originatorWalletId = "wallet1",
//      beneficiarySubwalletType = None,
//      beneficiaryWalletId = None,
//      id = "tx2"
//    )
//
//    val result = validationService.validateTransaction(transaction)
//    result shouldBe Left(OriginatorSubwalletTypeValidationError("External transaction not allowed on Investment type"))
//  }
//
//  it should "validate a withdrawal transaction with sufficient funds" in {
//    val transaction = Transaction(
//      transactionType = TransactionType.Withdraw,
//      originatorSubwalletType = SubwalletType.RealMoney,
//      amount = BigDecimal(50),
//      originatorWalletId = "wallet1",
//      beneficiarySubwalletType = None,
//      beneficiaryWalletId = None,
//      id = "tx3"
//    )
//
//    when(walletsRepo.findById("wallet1")).thenReturn(Right(Wallet("wallet1", WalletType.RealMoney)))
//    when(walletsService.getAvailableBalance(any())).thenReturn(BigDecimal(100))
//
//    val result = validationService.validateTransaction(transaction)
//    result shouldBe Right(())
//  }
//
//  it should "fail a withdrawal transaction with insufficient funds" in {
//    val transaction = Transaction(
//      transactionType = TransactionType.Withdraw,
//      originatorSubwalletType = SubwalletType.RealMoney,
//      amount = BigDecimal(150),
//      originatorWalletId = "wallet1",
//      beneficiarySubwalletType = None,
//      beneficiaryWalletId = None,
//      id = "tx4"
//    )
//
//    when(walletsRepo.findById("wallet1")).thenReturn(Right(Wallet("wallet1", WalletType.RealMoney)))
//    when(walletsService.getAvailableBalance(any())).thenReturn(BigDecimal(100))
//
//    val result = validationService.validateTransaction(transaction)
//    result shouldBe Left(InsufficientFundsValidationError("Wallet wallet1 has no sufficient funds"))
//  }
//
//  // Additional tests for Hold, Transfer, and TransferFromHold can be added similarly
//
//  it should "validate a transfer between valid subwallet types" in {
//    val transaction = Transaction(
//      transactionType = TransactionType.Transfer,
//      originatorSubwalletType = SubwalletType.RealMoney,
//      amount = BigDecimal(50),
//      originatorWalletId = "wallet1",
//      beneficiarySubwalletType = Some(SubwalletType.EmergencyFunds),
//      beneficiaryWalletId = None,
//      id = "tx5"
//    )
//
//    val result = validationService.validateTransaction(transaction)
//    result shouldBe Right(())
//  }
//
//  it should "fail a transfer between invalid subwallet types" in {
//    val transaction = Transaction(
//      transactionType = TransactionType.Transfer,
//      originatorSubwalletType = SubwalletType.RealMoney,
//      amount = BigDecimal(50),
//      originatorWalletId = "wallet1",
//      beneficiarySubwalletType = Some(SubwalletType.Investment), // Invalid pair
//      beneficiaryWalletId = None,
//      id = "tx6"
//    )
//
//    val result = validationService.validateTransaction(transaction)
//    result shouldBe Left(TransferBetweenSubwalletsValidationError("Transfer not allowed between RealMoney and Investment types"))
//  }
}