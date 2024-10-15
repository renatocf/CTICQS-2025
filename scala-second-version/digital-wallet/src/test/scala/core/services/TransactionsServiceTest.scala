package core.services

import adapters.{InvestmentPolicyInMemoryDatabase, TransactionInMemoryDatabase, WalletsInMemoryDatabase}
import core.domain.entities.Transaction
import core.domain.enums.{BalanceType, SubwalletType, TransactionStatus, TransactionType, WalletType}
import core.domain.model.{CreateJournalEntry, CreateTransactionRequest}
import core.errors.InsufficientFundsValidationError
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq}
import org.mockito.Mockito.{clearInvocations, mock, verify, when}
import org.mockito.ArgumentMatchers.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.TestUtils

import java.time.LocalDateTime
import java.util.UUID

class TransactionsServiceTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {
  private val ledgerServiceMock: LedgerService = mock(classOf[LedgerService])
  private val walletsServiceMock: WalletsService = mock(classOf[WalletsService])
  private val transactionValidationServiceMock: TransactionValidationService = mock(classOf[TransactionValidationService])
  private val partnerServiceMock: PartnerService = mock(classOf[PartnerService])

  private val investmentPolicyRepo = InvestmentPolicyInMemoryDatabase()
  private val transactionsRepo = TransactionInMemoryDatabase()
  private val walletsRepo = WalletsInMemoryDatabase()

  private val transactionService = TransactionsService(transactionsRepo, transactionValidationServiceMock, partnerServiceMock, ledgerServiceMock)

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
    clearInvocations(ledgerServiceMock, walletsServiceMock)

    utils.clearInMemoryDatabase(
      walletsDatabaseInMemory = Some(walletsRepo),
      investmentPolicyDatabaseInMemory = Some(investmentPolicyRepo)
    )

    super.afterEach()
  }

  behavior of "TransactionService"

  it should "create transaction" in {
    val request = CreateTransactionRequest(
      transactionType = TransactionType.Deposit,
      amount = BigDecimal(100),
      originatorSubwalletType = SubwalletType.RealMoney,
      originatorWalletId = "realMoneyWalletId",
      idempotencyKey = "idempotencyKey",
    )

    val result = transactionService.create(request)

    result match {
      case Right(transaction) =>
        transaction.transactionType shouldBe TransactionType.Deposit
        transaction.amount shouldBe BigDecimal(100)
        transaction.originatorWalletId shouldBe "realMoneyWalletId"
        transaction.originatorSubwalletType shouldBe SubwalletType.RealMoney
        transaction.idempotencyKey shouldBe "idempotencyKey"
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }
  }

  it should "process deposit" in {
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

    when(transactionValidationServiceMock.validateTransaction(any())).thenReturn(Right(()))

    val result = transactionService.process(transaction)

    val expectedJournalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = None,
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Internal,
        amount = -BigDecimal(100)
      )
    )

    result match {
      case Right((transactionResult, journalEntries, transactionStatus, action)) =>
        transactionResult shouldBe transaction
        journalEntries shouldBe expectedJournalEntries
        transactionStatus shouldBe TransactionStatus.Completed
        action shouldBe None
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }
  }

  it should "process withdrawal" in {
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

    when(transactionValidationServiceMock.validateTransaction(any())).thenReturn(Right(()))

    val result = transactionService.process(transaction)

    val expectedJournalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = None,
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Internal,
        amount = BigDecimal(100)
      )
    )

    result match {
      case Right((transactionResult, journalEntries, transactionStatus, action)) =>
        transactionResult shouldBe transaction
        journalEntries shouldBe expectedJournalEntries
        transactionStatus shouldBe TransactionStatus.Completed
        action shouldBe None
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }
  }

  it should "process hold" in {
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

    when(transactionValidationServiceMock.validateTransaction(any())).thenReturn(Right(()))

    val result = transactionService.process(transaction)

    val expectedJournalEntries = List(
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

    result match {
      case Right((transactionResult, journalEntries, transactionStatus, action)) =>
        transactionResult shouldBe transaction
        journalEntries shouldBe expectedJournalEntries
        transactionStatus shouldBe TransactionStatus.Processing
        action shouldBe None
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }
  }

  it should "process transfer" in {
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

    when(transactionValidationServiceMock.validateTransaction(any())).thenReturn(Right(()))

    val result = transactionService.process(transaction)

    val expectedJournalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = Some("emergencyFundsWalletId"),
        subwalletType = SubwalletType.EmergencyFunds,
        balanceType = BalanceType.Available,
        amount = BigDecimal(100)
      )
    )

    result match {
      case Right((transactionResult, journalEntries, transactionStatus, action)) =>
        transactionResult shouldBe transaction
        journalEntries shouldBe expectedJournalEntries
        transactionStatus shouldBe TransactionStatus.Completed
        action.isDefined shouldBe true
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }
  }

  it should "process transfer from hold" in {
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

    when(transactionValidationServiceMock.validateTransaction(any())).thenReturn(Right(()))

    val result = transactionService.process(transaction)

    val expectedJournalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.Stock,
        balanceType = BalanceType.Available,
        amount = BigDecimal(100)
      )
    )

    result match {
      case Right((transactionResult, journalEntries, transactionStatus, action)) =>
        transactionResult shouldBe transaction
        journalEntries shouldBe expectedJournalEntries
        transactionStatus shouldBe TransactionStatus.Completed
        action.isDefined shouldBe true
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }
  }

  it should "process fails when transaction is invalid" in {
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

    when(transactionValidationServiceMock.validateTransaction(any())).thenReturn(Left(InsufficientFundsValidationError("message")))

    val result = transactionService.process(transaction)

    result match {
      case Left(error) =>
        error.message shouldBe "message"
      case Right(_) =>
        fail(s"Expected Left but got Right")
    }
  }

  it should "execute deposit" in {
    val id = UUID.randomUUID().toString

    val transaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      id = id,
      transactionType = TransactionType.Deposit,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = None,
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Internal,
        amount = -BigDecimal(100)
      )
    )

    val tuple: ProcessTransactionTuple = (transaction, journalEntries, TransactionStatus.Completed, None)

    when(ledgerServiceMock.postJournalEntries(any())).thenReturn(LocalDateTime.now())

    val result = transactionService.execute(tuple)

    result match {
      case Right(transactionResult) =>
        transactionResult.id shouldBe id
        transactionResult.status shouldBe TransactionStatus.Completed
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }

    verify(ledgerServiceMock).postJournalEntries(ArgumentMatchers.eq(journalEntries))
  }

  it should "execute withdrawal" in {
    val id = UUID.randomUUID().toString

    val transaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      id = id,
      transactionType = TransactionType.Withdraw,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = None,
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Internal,
        amount = BigDecimal(100)
      )
    )

    val tuple: ProcessTransactionTuple = (transaction, journalEntries, TransactionStatus.Completed, None)

    when(ledgerServiceMock.postJournalEntries(any())).thenReturn(LocalDateTime.now())

    val result = transactionService.execute(tuple)

    result match {
      case Right(transactionResult) =>
        transactionResult.id shouldBe id
        transactionResult.status shouldBe TransactionStatus.Completed
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }

    verify(ledgerServiceMock).postJournalEntries(ArgumentMatchers.eq(journalEntries))
  }

  it should "execute transfer" in {
    val id = UUID.randomUUID().toString
    val transaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      id = id,
      transactionType = TransactionType.Transfer,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.EmergencyFunds),
      beneficiaryWalletId = Some("emergencyFundsWalletId"),
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Available,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = Some("emergencyFundsWalletId"),
        subwalletType = SubwalletType.EmergencyFunds,
        balanceType = BalanceType.Available,
        amount = BigDecimal(100)
      )
    )

    val action: Action = partnerServiceMock.executeInternalTransfer

    val tuple: ProcessTransactionTuple = (transaction, journalEntries, TransactionStatus.Completed, Some(action))

    when(partnerServiceMock.executeInternalTransfer(any())).thenReturn(Right(()))
    when(ledgerServiceMock.postJournalEntries(any())).thenReturn(LocalDateTime.now())

    val result = transactionService.execute(tuple)

    result match {
      case Right(transactionResult) =>
        transactionResult.id shouldBe id
        transactionResult.status shouldBe TransactionStatus.Completed
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }

    verify(ledgerServiceMock).postJournalEntries(ArgumentMatchers.eq(journalEntries))
  }

  it should "execute hold" in {
    val id = UUID.randomUUID().toString

    val transaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      id = id,
      transactionType = TransactionType.Hold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = None,
      beneficiaryWalletId = None,
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
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

    when(ledgerServiceMock.postJournalEntries(any())).thenReturn(LocalDateTime.now())

    val result = transactionService.execute(tuple)

    result match {
      case Right(transactionResult) =>
        transactionResult.id shouldBe id
        transactionResult.status shouldBe TransactionStatus.Processing
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }

    verify(ledgerServiceMock).postJournalEntries(ArgumentMatchers.eq(journalEntries))
  }

  it should "execute transfer from hold" in {
    val id = UUID.randomUUID().toString
    val transaction = utils.insertTransactionInMemory(
      db = transactionsRepo,
      id = id,
      transactionType = TransactionType.TransferFromHold,
      originatorSubwalletType = SubwalletType.RealMoney,
      amount = BigDecimal(100),
      originatorWalletId = "realMoneyWalletId",
      beneficiarySubwalletType = Some(SubwalletType.RealEstate),
      beneficiaryWalletId = Some("investmentWalletId"),
      insertedAt = LocalDateTime.now(),
      status = TransactionStatus.Creating
    )

    val journalEntries = List(
      CreateJournalEntry(
        walletId = Some("realMoneyWalletId"),
        subwalletType = SubwalletType.RealMoney,
        balanceType = BalanceType.Holding,
        amount = -BigDecimal(100)
      ),
      CreateJournalEntry(
        walletId = Some("investmentWalletId"),
        subwalletType = SubwalletType.RealEstate,
        balanceType = BalanceType.Available,
        amount = BigDecimal(100)
      )
    )

    val action: Action = partnerServiceMock.executeInternalTransfer

    val tuple: ProcessTransactionTuple = (transaction, journalEntries, TransactionStatus.Completed, Some(action))

    when(partnerServiceMock.executeInternalTransfer(any())).thenReturn(Right(()))
    when(ledgerServiceMock.postJournalEntries(any())).thenReturn(LocalDateTime.now())

    val result = transactionService.execute(tuple)

    result match {
      case Right(transactionResult) =>
        transactionResult.id shouldBe id
        transactionResult.status shouldBe TransactionStatus.Completed
      case Left(error) =>
        fail(s"Expected Right but got Left with error: $error")
    }

    verify(ledgerServiceMock).postJournalEntries(ArgumentMatchers.eq(journalEntries))
  }
}
