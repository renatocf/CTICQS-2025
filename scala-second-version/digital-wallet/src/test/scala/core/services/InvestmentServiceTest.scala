package core.services

import adapters.{InvestmentPolicyInMemoryDatabase, TransactionInMemoryDatabase, WalletsInMemoryDatabase}
import core.domain.entities.{InvestmentPolicy, Transaction}
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.enums.{BalanceType, SubwalletType, TransactionStatus, TransactionType, WalletType}
import core.domain.model.{CreateJournalEntry, CreateTransactionRequest, MovementRequest}
import core.errors.{ExecuteTransactionFailed, ExecutionError, InvestmentFailedError, InvestmentServiceInternalError, ProcessError, ProcessTransactionFailed}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{clearInvocations, doNothing, mock, never, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ports.TransactionFilter
import utils.TestUtils

import java.time.LocalDateTime
import java.util.UUID

class InvestmentServiceTest  extends AnyFlatSpec with BeforeAndAfterEach with Matchers {
  private val transactionsServiceMock: TransactionsService = mock(classOf[TransactionsService])

  private val investmentPolicyRepo = InvestmentPolicyInMemoryDatabase()
  private val transactionsRepo = TransactionInMemoryDatabase()
  private val walletsRepo = WalletsInMemoryDatabase()

  private val investmentService = InvestmentService(transactionsRepo, walletsRepo, investmentPolicyRepo, transactionsServiceMock)

  val utils = TestUtils()

  override def beforeEach(): Unit = {
    super.beforeEach()

    val investmentPolicy = utils.insertInvestmentPolicyInMemory(
      db = investmentPolicyRepo,
      id = "policyId",
      allocationStrategy = Map(
        SubwalletType.RealEstate -> BigDecimal(0.5),
        SubwalletType.Cryptocurrency -> BigDecimal(0),
        SubwalletType.Bonds -> BigDecimal(0),
        SubwalletType.Stock -> BigDecimal(0.5)
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

    val investmentPolicy2 = utils.insertInvestmentPolicyInMemory(
      db = investmentPolicyRepo,
      id = "policyId2",
      allocationStrategy = Map(
        SubwalletType.RealEstate -> BigDecimal(0),
        SubwalletType.Cryptocurrency -> BigDecimal(0),
        SubwalletType.Bonds -> BigDecimal(1),
        SubwalletType.Stock -> BigDecimal(0)
      )
    )

    val customerId2 = "cust_456"

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "realMoneyWalletId2",
      customerId = customerId2,
      walletType = WalletType.RealMoney,
      policyId = Some(investmentPolicy2.id)
    )

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "investmentWalletId2",
      customerId = customerId2,
      walletType = WalletType.Investment,
      policyId = Some(investmentPolicy2.id)
    )

    utils.insertWalletInMemory(
      db = walletsRepo,
      id = "emergencyFundsWalletId",
      customerId = customerId,
      walletType = WalletType.EmergencyFunds,
    )
  }

  override def afterEach(): Unit = {
    clearInvocations(transactionsServiceMock)

    utils.clearInMemoryDatabase(
      transactionsDatabaseInMemory = Some(transactionsRepo),
      walletsDatabaseInMemory = Some(walletsRepo),
      investmentPolicyDatabaseInMemory = Some(investmentPolicyRepo)
    )

    super.afterEach()
  }

  behavior of "InvestmentService"

  it should "execute movement with investment policy - hold" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "investmentWalletId",
      transactionType = TransactionType.Hold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.Hold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Holding,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.Hold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.RealEstate,
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Holding,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Right(secondTuple))

    when(transactionsServiceMock.execute(any)).thenReturn(Right(firstTransactionProcessing), Right(secondTransactionProcessing))

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result.isRight shouldBe true

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_Stock",
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.Stock,
        transactionType = TransactionType.Hold
      ))
    )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_RealEstate",
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.RealEstate,
        transactionType = TransactionType.Hold
      ))
    )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))
  }

  it should "execute movement with investment policy - hold processing fails" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "investmentWalletId",
      transactionType = TransactionType.Hold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.Hold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Holding,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.Hold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.RealEstate,
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Left(ProcessError("message")))

    doNothing().when(transactionsServiceMock).failBatch(any())

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result match {
      case Left(error) =>
        error shouldBe ProcessTransactionFailed(s"Failed to process transaction ${secondTransaction.id}: message")
      case Right(_) =>
        fail(s"Expected Left but got Right")
    }

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_Stock",
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.Stock,
        transactionType = TransactionType.Hold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_RealEstate",
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.RealEstate,
        transactionType = TransactionType.Hold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))

    verify(transactionsServiceMock).failBatch(ArgumentMatchers.eq("idempotencyKey"))
  }

  it should "execute movement with investment policy - hold execution fails" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "investmentWalletId",
      transactionType = TransactionType.Hold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.Hold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Holding,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.Hold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.RealEstate,
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Holding,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Right(secondTuple))

    when(transactionsServiceMock.execute(any)).thenReturn(Right(firstTransactionProcessing), Left(ExecutionError("message")))

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result match {
      case Left(error) =>
        error shouldBe ExecuteTransactionFailed(s"1 transaction(s) failed: message")
      case Right(_) =>
        fail(s"Expected Left but got Right")
    }

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_Stock",
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.Stock,
        transactionType = TransactionType.Hold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))

    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_RealEstate",
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.RealEstate,
        transactionType = TransactionType.Hold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))

    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))
  }

  it should "execute movement with investment policy - transfer from hold" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "realMoneyWalletId",
      walletSubwalletType = Some(SubwalletType.RealMoney),
      targetWalletId = Some("investmentWalletId"),
      transactionType = TransactionType.TransferFromHold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Right(secondTuple))

    when(transactionsServiceMock.execute(any)).thenReturn(Right(firstTransactionProcessing), Right(secondTransactionProcessing))

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result.isRight shouldBe true

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_Stock",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.Stock),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_RealEstate",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealEstate),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))
  }

  it should "execute movement with investment policy - missing required arguments" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "realMoneyWalletId",
      transactionType = TransactionType.TransferFromHold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result match {
      case Left(error) =>
        error shouldBe InvestmentServiceInternalError("Missing wallet subwallet type")
      case Right(_) =>
        fail(s"Expected Left but got Right")
    }
  }

  it should "execute movement with investment policy - transfer from hold processing fails" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "realMoneyWalletId",
      walletSubwalletType = Some(SubwalletType.RealMoney),
      targetWalletId = Some("investmentWalletId"),
      transactionType = TransactionType.TransferFromHold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Left(ProcessError("message")))

    doNothing().when(transactionsServiceMock).failBatch(any())

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result match {
      case Left(error) =>
        error shouldBe ProcessTransactionFailed(s"Failed to process transaction ${secondTransaction.id}: message")
      case Right(_) =>
        fail(s"Expected Left but got Right")
    }

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_Stock",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.Stock),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_RealEstate",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealEstate),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))

    verify(transactionsServiceMock).failBatch("idempotencyKey")
  }

  it should "execute movement with investment policy - transfer from hold execution fails" in {
    val request = MovementRequest(
      amount = BigDecimal(100),
      idempotencyKey = "idempotencyKey",
      walletId = "realMoneyWalletId",
      walletSubwalletType = Some(SubwalletType.RealMoney),
      targetWalletId = Some("investmentWalletId"),
      transactionType = TransactionType.TransferFromHold,
      investmentPolicy = InvestmentPolicy(
        id = "policyId",
        allocationStrategy = Map(
          SubwalletType.Cryptocurrency -> BigDecimal(0),
          SubwalletType.Bonds -> BigDecimal(0),
          SubwalletType.Stock -> BigDecimal(0.5),
          SubwalletType.RealEstate -> BigDecimal(0.5),
        )
      )
    )

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Right(secondTuple))

    when(transactionsServiceMock.execute(any)).thenReturn(Right(firstTransaction), Left(ExecutionError("message")))

    doNothing().when(transactionsServiceMock).failBatch(any())

    val result = investmentService.executeMovementWithInvestmentPolicy(request)

    result match {
      case Left(error) =>
        error shouldBe ExecuteTransactionFailed(s"1 transaction(s) failed: message")
      case Right(_) =>
        fail(s"Expected Left but got Right")
    }

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_Stock",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.Stock),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))

    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey"),
        idempotencyKey = "idempotencyKey_RealEstate",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealEstate),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))

    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))
  }

  it should "buyFunds" in {
    val firstInvestmentTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey1",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    val secondInvestmentTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey2",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(50),
      originatorWalletId = "realMoneyWalletId2",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    // investment from realMoneyWalletId

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey1"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey1_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey1"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey1_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    // investment from realMoneyWalletId2

    val thirdTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey2"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId2",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId2"),
      beneficiarySubwalletType = Some(SubwalletType.Bonds),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey2_Bonds",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val thirdTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries3 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId2"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId2"),
        subwalletType = SubwalletType.Bonds,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val thirdTuple: ProcessTransactionTuple = (thirdTransaction, journalEntries3, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any)).thenReturn(Right(firstTransaction), Right(secondTransaction), Right(thirdTransaction))

    when(transactionsServiceMock.process(any)).thenReturn(Right(firstTuple), Right(secondTuple), Right(thirdTuple))

    when(transactionsServiceMock.execute(any)).thenReturn(Right(firstTransactionProcessing), Right(secondTransactionProcessing), Right(thirdTransactionProcessing))

    investmentService.buyFunds()

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey1"),
        idempotencyKey = "idempotencyKey1_Stock",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.Stock),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey1"),
        idempotencyKey = "idempotencyKey1_RealEstate",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealEstate),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))

    // third transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey2"),
        idempotencyKey = "idempotencyKey2_Bonds",
        originatorWalletId = "realMoneyWalletId2",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId2"),
        beneficiarySubwalletType = Some(SubwalletType.Bonds),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(thirdTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(thirdTuple))

    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(firstInvestmentTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(secondInvestmentTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))
  }

  it should "buyFunds - movement processing fails" in {
    val firstInvestmentTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey1",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    val secondInvestmentTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey2",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(50),
      originatorWalletId = "realMoneyWalletId2",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    // investment from realMoneyWalletId

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey1"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey1_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey1"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey1_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    // investment from realMoneyWalletId2

    val thirdTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey2"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId2",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId2"),
      beneficiarySubwalletType = Some(SubwalletType.Bonds),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey2_Bonds",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(transactionsServiceMock.create(any[CreateTransactionRequest])).thenAnswer { invocation =>
      val request: CreateTransactionRequest = invocation.getArgument(0)
      request.idempotencyKey match {
        case "idempotencyKey1_Stock" => Right(firstTransaction)
        case "idempotencyKey1_RealEstate" => Right(secondTransaction)
        case "idempotencyKey2_Bonds" => Right(thirdTransaction)
      }
    }

    when(transactionsServiceMock.process(any[Transaction])).thenAnswer { invocation =>
      val transaction: Transaction = invocation.getArgument(0)
      transaction.id match {
        case firstTransaction.id => Right(firstTuple)
        case secondTransaction.id => Right(secondTuple)
        case thirdTransaction.id => Left(ProcessError("message"))
      }
    }

    when(transactionsServiceMock.execute(any[ProcessTransactionTuple])).thenAnswer { invocation =>
      val tuple: ProcessTransactionTuple = invocation.getArgument(0)
      tuple._1.id match {
        case firstTuple._1.id => Right(firstTransactionProcessing)
        case secondTuple._1.id => Right(secondTransactionProcessing)
      }
    }

    investmentService.buyFunds()

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey1"),
        idempotencyKey = "idempotencyKey1_Stock",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.Stock),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey1"),
        idempotencyKey = "idempotencyKey1_RealEstate",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealEstate),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))

    // third transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey2"),
        idempotencyKey = "idempotencyKey2_Bonds",
        originatorWalletId = "realMoneyWalletId2",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId2"),
        beneficiarySubwalletType = Some(SubwalletType.Bonds),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(thirdTransaction))

    // more verifications
    verify(transactionsServiceMock).failBatch(ArgumentMatchers.eq("idempotencyKey2"))
    verify(transactionsServiceMock).releaseHold(ArgumentMatchers.eq(secondInvestmentTransaction))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(firstInvestmentTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(secondInvestmentTransaction.id), ArgumentMatchers.eq(TransactionStatus.Failed))
  }

  it should "buyFunds - movement execution fails" in {
    val firstInvestmentTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey1",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    val secondInvestmentTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey2",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(50),
      originatorWalletId = "realMoneyWalletId2",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    // investment from realMoneyWalletId

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey1"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.Stock),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey1_Stock",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey1"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey1_RealEstate",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    // investment from realMoneyWalletId2

    val thirdTransaction = Transaction(
      id = UUID.randomUUID().toString,
      batchId = Some("idempotencyKey2"),
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "realMoneyWalletId2",
      originatorSubwalletType = SubwalletType.RealMoney,
      beneficiaryWalletId = Some("investmentWalletId2"),
      beneficiarySubwalletType = Some(SubwalletType.Bonds),
      amount = BigDecimal("50.0"),
      idempotencyKey = "idempotencyKey2_Bonds",
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val journalEntries3 = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId2"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId2"),
        subwalletType = SubwalletType.Bonds,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val thirdTuple: ProcessTransactionTuple = (thirdTransaction, journalEntries3, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any[CreateTransactionRequest])).thenAnswer { invocation =>
      val request: CreateTransactionRequest = invocation.getArgument(0)
      request.idempotencyKey match {
        case "idempotencyKey1_Stock" => Right(firstTransaction)
        case "idempotencyKey1_RealEstate" => Right(secondTransaction)
        case "idempotencyKey2_Bonds" => Right(thirdTransaction)
      }
    }

    when(transactionsServiceMock.process(any[Transaction])).thenAnswer { invocation =>
      val transaction: Transaction = invocation.getArgument(0)
      transaction.id match {
        case firstTransaction.id => Right(firstTuple)
        case secondTransaction.id => Right(secondTuple)
        case thirdTransaction.id => Right(thirdTuple)
      }
    }

    when(transactionsServiceMock.execute(any[ProcessTransactionTuple])).thenAnswer { invocation =>
      val tuple: ProcessTransactionTuple = invocation.getArgument(0)
      tuple._1.id match {
        case firstTuple._1.id => Right(firstTransactionProcessing)
        case secondTuple._1.id => Right(secondTransactionProcessing)
        case thirdTuple._1.id => Left(ExecutionError("message"))
      }
    }

    investmentService.buyFunds()

    // first transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey1"),
        idempotencyKey = "idempotencyKey1_Stock",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.Stock),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))

    // second transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey1"),
        idempotencyKey = "idempotencyKey1_RealEstate",
        originatorWalletId = "realMoneyWalletId",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealEstate),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(secondTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(secondTuple))

    // third transaction
    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        batchId = Some("idempotencyKey2"),
        idempotencyKey = "idempotencyKey2_Bonds",
        originatorWalletId = "realMoneyWalletId2",
        originatorSubwalletType = SubwalletType.RealMoney,
        beneficiaryWalletId = Some("investmentWalletId2"),
        beneficiarySubwalletType = Some(SubwalletType.Bonds),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(thirdTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(thirdTuple))

    // more verifications
    verify(transactionsServiceMock, never).failBatch(any)
    verify(transactionsServiceMock, never).releaseHold(any)
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(firstInvestmentTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))
    verify(transactionsServiceMock, never).updateStatus(ArgumentMatchers.eq(secondInvestmentTransaction.id), any)
  }
  
  it should "sellFunds" in {
    val firstLiquidationTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey1",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal(50),
      originatorWalletId = "investmentWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    val secondLiquidationTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey2",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      amount = BigDecimal(100),
      originatorWalletId = "investmentWalletId2",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    // liquidation from investmentWalletId

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      beneficiaryWalletId = Some("realMoneyWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      amount = BigDecimal("50.0"),
      idempotencyKey = firstLiquidationTransaction.id,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)
    
    // liquidation from investmentWalletId2

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId2",
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      beneficiaryWalletId = Some("realMoneyWalletId2"),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      amount = BigDecimal("100.0"),
      idempotencyKey = secondLiquidationTransaction.id,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId2"),
        subwalletType = SubwalletType.Cryptocurrency,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("100.0")
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId2"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal("100.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any[CreateTransactionRequest])).thenAnswer { invocation =>
      val request: CreateTransactionRequest = invocation.getArgument(0)
      request.idempotencyKey match {
        case firstLiquidationTransaction.id => Right(firstTransaction)
        case secondLiquidationTransaction.id  => Right(secondTransaction)
      }
    }

    when(transactionsServiceMock.process(any[Transaction])).thenAnswer { invocation =>
      val transaction: Transaction = invocation.getArgument(0)
      transaction.id match {
        case firstTransaction.id => Right(firstTuple)
        case secondTransaction.id => Right(secondTuple)
      }
    }

    when(transactionsServiceMock.execute(any[ProcessTransactionTuple])).thenAnswer { invocation =>
      val tuple: ProcessTransactionTuple = invocation.getArgument(0)
      tuple._1.id match {
        case firstTuple._1.id => Right(firstTransactionProcessing)
        case secondTuple._1.id => Right(secondTransactionProcessing)
      }
    }

    investmentService.sellFunds()

    // first liquidation transaction

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        idempotencyKey = firstLiquidationTransaction.id,
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.Stock,
        beneficiaryWalletId = Some("realMoneyWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealMoney),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(firstLiquidationTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))

    // second liquidation transaction

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("100.0"),
        idempotencyKey = secondLiquidationTransaction.id,
        originatorWalletId = "investmentWalletId2",
        originatorSubwalletType = SubwalletType.Cryptocurrency,
        beneficiaryWalletId = Some("realMoneyWalletId2"),
        beneficiarySubwalletType = Some(SubwalletType.RealMoney),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(secondLiquidationTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))
  }

  it should "sellFunds - transfer from hold processing fails" in {
    val firstLiquidationTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey1",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal(50),
      originatorWalletId = "investmentWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    val secondLiquidationTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey2",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      amount = BigDecimal(100),
      originatorWalletId = "investmentWalletId2",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    // liquidation from investmentWalletId

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      beneficiaryWalletId = Some("realMoneyWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      amount = BigDecimal("50.0"),
      idempotencyKey = firstLiquidationTransaction.id,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    // liquidation from investmentWalletId2

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId2",
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      beneficiaryWalletId = Some("realMoneyWalletId2"),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      amount = BigDecimal("100.0"),
      idempotencyKey = secondLiquidationTransaction.id,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    when(transactionsServiceMock.create(any[CreateTransactionRequest])).thenAnswer { invocation =>
      val request: CreateTransactionRequest = invocation.getArgument(0)
      request.idempotencyKey match {
        case firstLiquidationTransaction.id => Right(firstTransaction)
        case secondLiquidationTransaction => Right(secondTransaction)
      }
    }

    when(transactionsServiceMock.process(any[Transaction])).thenAnswer { invocation =>
      val transaction: Transaction = invocation.getArgument(0)
      transaction.id match {
        case firstTransaction.id => Right(firstTuple)
        case secondTransaction.id => Left(ProcessError("message"))
      }
    }

    when(transactionsServiceMock.execute(any[ProcessTransactionTuple])).thenAnswer { invocation =>
      val tuple: ProcessTransactionTuple = invocation.getArgument(0)
      tuple._1.id match {
        case firstTuple._1.id => Right(firstTransactionProcessing)
      }
    }

    investmentService.sellFunds()

    // first liquidation transaction

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        idempotencyKey = firstLiquidationTransaction.id,
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.Stock,
        beneficiaryWalletId = Some("realMoneyWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealMoney),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(firstLiquidationTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))

    // second liquidation transaction

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("100.0"),
        idempotencyKey = secondLiquidationTransaction.id,
        originatorWalletId = "investmentWalletId2",
        originatorSubwalletType = SubwalletType.Cryptocurrency,
        beneficiaryWalletId = Some("realMoneyWalletId2"),
        beneficiarySubwalletType = Some(SubwalletType.RealMoney),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))

    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(secondTransaction.id), ArgumentMatchers.eq(TransactionStatus.Failed))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(secondLiquidationTransaction.id), ArgumentMatchers.eq(TransactionStatus.Failed))
    verify(transactionsServiceMock).releaseHold(ArgumentMatchers.eq(secondLiquidationTransaction))
  }

  it should "sellFunds - transfer from hold execution fails" in {
    val firstLiquidationTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey1",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.Stock,
      amount = BigDecimal(50),
      originatorWalletId = "investmentWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    val secondLiquidationTransaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      idempotencyKey = "idempotencyKey2",
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      amount = BigDecimal(100),
      originatorWalletId = "investmentWalletId2",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Processing
    )

    // liquidation from investmentWalletId

    val firstTransaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId",
      originatorSubwalletType = SubwalletType.Stock,
      beneficiaryWalletId = Some("realMoneyWalletId"),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      amount = BigDecimal("50.0"),
      idempotencyKey = firstLiquidationTransaction.id,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val firstTransactionProcessing = Transaction(
      id = firstTransaction.id,
      transactionType = firstTransaction.transactionType,
      originatorSubwalletType = firstTransaction.originatorSubwalletType,
      amount = firstTransaction.amount,
      originatorWalletId = firstTransaction.originatorWalletId,
      beneficiarySubwalletType = firstTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = firstTransaction.beneficiaryWalletId,
      idempotencyKey = firstTransaction.idempotencyKey,
      insertedAt = firstTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries1 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("50.0")
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal("50.0")
      )
    )

    val firstTuple: ProcessTransactionTuple = (firstTransaction, journalEntries1, TransactionStatus.Processing, None)

    // liquidation from investmentWalletId2

    val secondTransaction = Transaction(
      id = UUID.randomUUID().toString,
      transactionType = TransactionType.TransferFromHold,
      originatorWalletId = "investmentWalletId2",
      originatorSubwalletType = SubwalletType.Cryptocurrency,
      beneficiaryWalletId = Some("realMoneyWalletId2"),
      beneficiarySubwalletType = Some(SubwalletType.RealMoney),
      amount = BigDecimal("100.0"),
      idempotencyKey = secondLiquidationTransaction.id,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val secondTransactionProcessing = Transaction(
      id = secondTransaction.id,
      transactionType = secondTransaction.transactionType,
      originatorSubwalletType = secondTransaction.originatorSubwalletType,
      amount = secondTransaction.amount,
      originatorWalletId = secondTransaction.originatorWalletId,
      beneficiarySubwalletType = secondTransaction.beneficiarySubwalletType,
      beneficiaryWalletId = secondTransaction.beneficiaryWalletId,
      idempotencyKey = secondTransaction.idempotencyKey,
      insertedAt = secondTransaction.insertedAt,
      status = TransactionStatus.Processing
    )

    val journalEntries2 = List(
      CreateJournalEntry(
        walletId = Some("investmentWalletId2"),
        subwalletType = SubwalletType.Cryptocurrency,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal("100.0")
      ),
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId2"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal("100.0")
      )
    )

    val secondTuple: ProcessTransactionTuple = (secondTransaction, journalEntries2, TransactionStatus.Processing, None)

    when(transactionsServiceMock.create(any[CreateTransactionRequest])).thenAnswer { invocation =>
      val request: CreateTransactionRequest = invocation.getArgument(0)
      request.idempotencyKey match {
        case firstLiquidationTransaction.id => Right(firstTransaction)
        case secondLiquidationTransaction.id => Right(secondTransaction)
      }
    }

    when(transactionsServiceMock.process(any[Transaction])).thenAnswer { invocation =>
      val transaction: Transaction = invocation.getArgument(0)
      transaction.id match {
        case firstTransaction.id => Right(firstTuple)
        case secondTransaction.id => Right(secondTuple)
      }
    }

    when(transactionsServiceMock.execute(any[ProcessTransactionTuple])).thenAnswer { invocation =>
      val tuple: ProcessTransactionTuple = invocation.getArgument(0)
      tuple._1.id match {
        case firstTuple._1.id => Right(firstTransactionProcessing)
        case secondTuple._1.id => Left(ExecutionError("message"))
      }
    }

    investmentService.sellFunds()

    // first liquidation transaction

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("50.0"),
        idempotencyKey = firstLiquidationTransaction.id,
        originatorWalletId = "investmentWalletId",
        originatorSubwalletType = SubwalletType.Stock,
        beneficiaryWalletId = Some("realMoneyWalletId"),
        beneficiarySubwalletType = Some(SubwalletType.RealMoney),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))
    verify(transactionsServiceMock).updateStatus(ArgumentMatchers.eq(firstLiquidationTransaction.id), ArgumentMatchers.eq(TransactionStatus.Completed))

    // second liquidation transaction

    verify(transactionsServiceMock)
      .create(ArgumentMatchers.eq(CreateTransactionRequest(
        amount = BigDecimal("100.0"),
        idempotencyKey = secondLiquidationTransaction.id,
        originatorWalletId = "investmentWalletId2",
        originatorSubwalletType = SubwalletType.Cryptocurrency,
        beneficiaryWalletId = Some("realMoneyWalletId2"),
        beneficiarySubwalletType = Some(SubwalletType.RealMoney),
        transactionType = TransactionType.TransferFromHold
      ))
      )

    verify(transactionsServiceMock).process(ArgumentMatchers.eq(firstTransaction))
    verify(transactionsServiceMock).execute(ArgumentMatchers.eq(firstTuple))
    verify(transactionsServiceMock, never).updateStatus(ArgumentMatchers.eq(secondLiquidationTransaction.id), any)
    verify(transactionsServiceMock, never).releaseHold(any)
  }
}
