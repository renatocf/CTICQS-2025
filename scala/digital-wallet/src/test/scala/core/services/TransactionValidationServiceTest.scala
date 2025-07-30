package core.services

import adapters.{InvestmentPolicyInMemoryDatabase, TransactionInMemoryDatabase, WalletsInMemoryDatabase}
import core.domain.entities.{Transaction, Wallet}
import core.domain.enums.{SubwalletType, TransactionStatus, TransactionType, WalletType}
import core.errors.{InsufficientFundsValidationError, OriginatorSubwalletTypeValidationError, TransferBetweenSubwalletsValidationError, TransferBetweenWalletsValidationError}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{clearInvocations, mock, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.TestUtils

import java.time.LocalDateTime
import java.util.UUID

class TransactionValidationServiceTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {
  private val ledgerServiceMock: LedgerService = mock(classOf[LedgerService])
  private val walletsServiceMock: WalletsService = mock(classOf[WalletsService])

  private val investmentPolicyRepo = InvestmentPolicyInMemoryDatabase()
  private val transactionsRepo= TransactionInMemoryDatabase()
  private val walletsRepo = WalletsInMemoryDatabase()

  private val transactionValidationService =
    TransactionValidationService(walletsRepo, walletsServiceMock, ledgerServiceMock)

  val utils = TestUtils()

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
      policyId = Some(investmentPolicy.id)
    )

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "investmentWalletId",
      customerId = customerId,
      walletType = WalletType.Investment,
      policyId = Some(investmentPolicy.id)
    )

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "emergencyFundsWalletId",
      customerId = customerId,
      walletType = WalletType.EmergencyFunds,
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

  behavior of "TransactionValidationService - Deposits"

  it should "validate a deposit transaction" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Deposit,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Right(())
  }

  it should "fail a deposit transaction with invalid originator subwallet type" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Deposit,
      originatorSubwalletType = SubwalletType.RealEstate,
      amount = BigDecimal(100),
      originatorWalletId = "InvestmentWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(OriginatorSubwalletTypeValidationError("External transaction not allowed on RealEstate type"))
  }

  behavior of "TransactionValidationService - Withdrawals"

  it should "validate a withdrawal transaction" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Withdraw,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getAvailableBalance(any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Right(())
  }

  it should "fail a withdrawal transaction with invalid originator subwallet type" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Withdraw,
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      amount = BigDecimal(100),
      originatorWalletId = "InvestmentWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(OriginatorSubwalletTypeValidationError("External transaction not allowed on Cryptocurrency type"))
  }

  it should "fail a withdrawal transaction with insufficient funds" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Withdraw,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(150),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getAvailableBalance(any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(InsufficientFundsValidationError("Wallet realMoneyWalletId has no sufficient funds"))
  }

  behavior of "TransactionValidationService - Transfers"

  it should "validate transfer transaction" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Transfer,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.EmergencyFunds),
      beneficiaryWalletId = Some("emergencyFundsWalletId"),
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getAvailableBalance(any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Right(())
  }

  it should "fail transfer transaction with insufficient funds" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Transfer,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(150),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.EmergencyFunds),
      beneficiaryWalletId = Some("emergencyFundsWalletId"),
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getAvailableBalance(any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(InsufficientFundsValidationError("Wallet realMoneyWalletId has no sufficient funds"))
  }

  it should "fail transfer transaction with invalid beneficiary subwallet type" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Transfer,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      beneficiaryWalletId = Some("investmentWalletId"),
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(TransferBetweenSubwalletsValidationError("Transfer not allowed between RealMoney and Stock types"))
  }

  behavior of "TransactionValidationService - Holds"

  it should "validate hold transaction" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(50),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getAvailableBalance(any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Right(())
  }

  it should "fail hold transaction with invalid originator subwallet type" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.EmergencyFunds,
      amount = BigDecimal(100),
      originatorWalletId = "InvestmentWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(OriginatorSubwalletTypeValidationError("External transaction not allowed on EmergencyFunds type"))
  }

  it should "fail a hold transaction with no sufficient funds" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(200),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getAvailableBalance(any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(InsufficientFundsValidationError("Wallet realMoneyWalletId has no sufficient funds"))
  }

  behavior of "TransactionValidationService - Transfer from hold"

  it should "validate transfer from hold transaction" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      beneficiaryWalletId = Some("investmentWalletId"),
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getSubwalletPendingBalance(any(), any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Right(())
  }

  it should "fail transfer from hold transaction with insufficient funds" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal(150),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      beneficiaryWalletId = Some("realMoneyWalletId"),
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(walletsServiceMock.getSubwalletPendingBalance(any(), any())).thenReturn(BigDecimal(100))

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(InsufficientFundsValidationError("Wallet investmentWalletId has no sufficient funds"))
  }

  it should "fail transfer from hold transaction with invalid beneficiary subwallet type" in {
    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.EmergencyFunds),
      beneficiaryWalletId = Some("emergencyFundsWalletId"),
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val result = transactionValidationService.validateTransaction(transaction)
    result shouldBe Left(TransferBetweenWalletsValidationError("Transfer not allowed between RealMoney and EmergencyFunds types"))
  }
}