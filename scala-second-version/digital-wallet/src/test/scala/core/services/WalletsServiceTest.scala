package core.services

import adapters.{InvestmentPolicyInMemoryDatabase, TransactionInMemoryDatabase, WalletsInMemoryDatabase}
import core.domain.entities.{InvestmentPolicy, Transaction}
import core.domain.enums.{BalanceType, SubwalletType, TransactionStatus, TransactionType, WalletType, TransactionType as transactionType}
import core.domain.model.{CreateJournalEntry, CreateTransactionRequest, InvestmentRequest, LiquidationRequest, MovementRequest}
import core.errors.{CreationError, ExecutionError, InvestmentFailedError, InvestmentServiceInternalError, LiquidationFailedError, ProcessError}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{clearInvocations, doNothing, mock, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.TestUtils

import java.time.LocalDateTime
import java.util.UUID

class WalletsServiceTest  extends AnyFlatSpec with BeforeAndAfterEach with Matchers {
  private val ledgerServiceMock: LedgerService = mock(classOf[LedgerService])
  private val walletsServiceMock: WalletsService = mock(classOf[WalletsService])
  private val transactionsServiceMock: TransactionsService = mock(classOf[TransactionsService])
  private val investmentServiceMock: InvestmentService = mock(classOf[InvestmentService])

  private val investmentPolicyRepo = InvestmentPolicyInMemoryDatabase()
  private val transactionsRepo = TransactionInMemoryDatabase()
  private val walletsRepo = WalletsInMemoryDatabase()

  private val walletsService = WalletsService(walletsRepo, investmentPolicyRepo, ledgerServiceMock, transactionsServiceMock, investmentServiceMock)

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
    clearInvocations(transactionsServiceMock, investmentServiceMock)

    utils.clearInMemoryDatabase(
      walletsDatabaseInMemory = Some(walletsRepo),
      investmentPolicyDatabaseInMemory = Some(investmentPolicyRepo)
    )

    super.afterEach()
  }

  behavior of "WalletsService"

  it should "invest" in {
    val request = InvestmentRequest(
      customerId = "cust_123",
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey"
    )

    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val transactionProcessing = Transaction(
      id = transaction.id,
      transactionType = transaction.transactionType,
      originatorSubwalletType = transaction.originatorSubwalletType,
      amount = transaction.amount,
      originatorWalletId = transaction.originatorWalletId,
      beneficiarySubwalletType = transaction.beneficiarySubwalletType,
      beneficiaryWalletId = transaction.beneficiaryWalletId,
      idempotencyKey = transaction.idempotencyKey,
      insertedAt = transaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = BigDecimal(100)
      )
    )

    val tuple: ProcessTransactionTuple = (transaction, journalEntries, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any())).thenReturn(Right(transaction))
    when(transactionsServiceMock.process(any())).thenReturn(Right(tuple))
    when(transactionsServiceMock.execute(any())).thenReturn(Right(transactionProcessing))

    val result = walletsService.invest(request)
    result match {
      case Right(transactionResult) =>
        transactionResult shouldBe transactionProcessing
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
          amount = request.amount,
          idempotencyKey = request.idempotencyKey,
          originatorWalletId = "realMoneyWalletId",
          originatorSubwalletType = SubwalletType.RealMoney,
          transactionType = TransactionType.Hold
        )
      )
    )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(transaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(tuple))
  }

  it should "invest - fails to create transaction" in {
    val request = InvestmentRequest(
      customerId = "cust_123",
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey"
    )

    when(transactionsServiceMock.create(any())).thenReturn(Left(CreationError("message")))

    val result = walletsService.invest(request)
    result match {
      case Left(error) =>
        error shouldBe InvestmentFailedError("message")
      case Right(_) =>
        fail(s"Expected Left but got Right with error")
    }

    verify(transactionsServiceMock).create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = request.amount,
        idempotencyKey = request.idempotencyKey,
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        transactionType = TransactionType.Hold
      ))
    )
  }

  it should "invest - fails to process transaction" in {
    val request = InvestmentRequest(
      customerId = "cust_123",
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey"
    )

    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(transactionsServiceMock.create(any())).thenReturn(Right(transaction))
    when(transactionsServiceMock.process(any())).thenReturn(Left(ProcessError("message")))
    doNothing().when(transactionsServiceMock).updateStatus(any(), any())

    val result = walletsService.invest(request)
    result match {
      case Left(error) =>
        error shouldBe InvestmentFailedError("message")
      case Right(_) =>
        fail(s"Expected Left but got Right with error")
    }

    verify(transactionsServiceMock).create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = request.amount,
        idempotencyKey = request.idempotencyKey,
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        transactionType = TransactionType.Hold
      ))
    )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(transaction))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(transaction.id), ArgumentMatchers.eq(TransactionStatus.Failed))
  }

  it should "invest - fails to execute transaction" in {
    val request = InvestmentRequest(
      customerId = "cust_123",
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey"
    )

    val transaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      idempotencyKey = "idempotencyKey",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val transactionProcessing = Transaction(
      id = transaction.id,
      transactionType = transaction.transactionType,
      originatorSubwalletType = transaction.originatorSubwalletType,
      amount = transaction.amount,
      originatorWalletId = transaction.originatorWalletId,
      beneficiarySubwalletType = transaction.beneficiarySubwalletType,
      beneficiaryWalletId = transaction.beneficiaryWalletId,
      idempotencyKey = transaction.idempotencyKey,
      insertedAt = transaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = BigDecimal(100)
      )
    )

    val tuple: ProcessTransactionTuple = (transaction, journalEntries, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any())).thenReturn(Right(transaction))
    when(transactionsServiceMock.process(any())).thenReturn(Right(tuple))
    when(transactionsServiceMock.execute(any())).thenReturn(Left(ExecutionError("message")))

    val result = walletsService.invest(request)
    result match {
      case Left(error) =>
        error shouldBe InvestmentFailedError("message")
      case Right(_) =>
        fail(s"Expected Left but got Right with error")
    }

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = request.amount,
        idempotencyKey = request.idempotencyKey,
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        transactionType = TransactionType.Hold
      ))
    )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(transaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(tuple))
  }

  it should "liquidation" in {
    val request = LiquidationRequest(
      customerId = "cust_123",
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey"
    )

    when(investmentServiceMock.executeMovementWithInvestmentPolicy(any())).thenReturn(Right(()))

    val result = walletsService.liquidate(request)

    result.isRight shouldBe true

    verify(investmentServiceMock)
      .executeMovementWithInvestmentPolicy(ArgumentMatchers.eq(MovementRequest(
        amount = request.amount,
        idempotencyKey = request.idempotencyKey,
        walletId = "investmentWalletId",
        transactionType = TransactionType.Hold,
        investmentPolicy = InvestmentPolicy(
          id = "policyId",
          allocationStrategy = Map(
            SubwalletType.RealEstate -> BigDecimal(0.4),
            SubwalletType.Cryptocurrency -> BigDecimal(0.1),
            SubwalletType.Bonds -> BigDecimal(0.1),
            SubwalletType.Stock -> BigDecimal(0.4)
          )
        ),
      ))
    )
  }

  it should "liquidation - movement fails" in {
    val request = LiquidationRequest(
      customerId = "cust_123",
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey"
    )

    when(investmentServiceMock.executeMovementWithInvestmentPolicy(any()))
      .thenReturn(Left(InvestmentServiceInternalError("message")))

    val result = walletsService.liquidate(request)

    result match {
      case Left(error) =>
        error shouldBe LiquidationFailedError("message")
      case Right(_) =>
        fail("Expected Left but got Right with error")
    }

    verify(investmentServiceMock).executeMovementWithInvestmentPolicy(
      ArgumentMatchers.eq(MovementRequest(
        amount = request.amount,
        idempotencyKey = request.idempotencyKey,
        walletId = "investmentWalletId",
        transactionType = TransactionType.Hold,
        investmentPolicy = InvestmentPolicy(
          id = "policyId",
          allocationStrategy = Map(
            SubwalletType.RealEstate -> BigDecimal(0.4),
            SubwalletType.Cryptocurrency -> BigDecimal(0.1),
            SubwalletType.Bonds -> BigDecimal(0.1),
            SubwalletType.Stock -> BigDecimal(0.4)
          )
        )
      ))
    )
  }
}
