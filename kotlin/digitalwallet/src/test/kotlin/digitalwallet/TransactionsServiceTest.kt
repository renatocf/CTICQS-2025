@file:Suppress("ktlint:standard:no-wildcard-imports")

package digitalwallet

import digitalwallet.adapters.InvestmentPolicyInMemoryDatabase
import digitalwallet.adapters.TransactionsInMemoryDatabase
import digitalwallet.adapters.WalletsInMemoryDatabase
import digitalwallet.core.domain.enums.*
import digitalwallet.core.domain.models.CreateJournalEntry
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.exceptions.*
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.PartnerService
import digitalwallet.core.services.TransactionsService
import digitalwallet.ports.TransactionFilter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@MicronautTest
class TransactionsServiceTest : DescribeSpec() {
    private val ledgerServiceMock: LedgerService = mockk()
    private val partnerServiceMock: PartnerService = mockk()

    private val investmentPolicyInMemoryDatabase = InvestmentPolicyInMemoryDatabase()
    private val transactionsDatabaseInMemory = TransactionsInMemoryDatabase()
    private val walletsDatabaseInMemory = WalletsInMemoryDatabase()

    private val transactionsService =
        TransactionsService(transactionsDatabaseInMemory, walletsDatabaseInMemory, ledgerServiceMock, partnerServiceMock)

    init {
        beforeEach {
            coEvery { ledgerServiceMock.postJournalEntries(any()) } returns LocalDateTime.now()
            coEvery { partnerServiceMock.executeInternalTransfer(any()) } returns Unit
            coEvery {
                ledgerServiceMock.getBalance(any(), any())
            } returns BigDecimal(100)

            val investmentPolicy =
                insertInvestmentPolicyInMemory(
                    db = investmentPolicyInMemoryDatabase,
                    id = "policyId",
                    allocationStrategy =
                        mutableMapOf(
                            SubwalletType.REAL_ESTATE to BigDecimal(0.4),
                            SubwalletType.CRYPTOCURRENCY to BigDecimal(0.1),
                            SubwalletType.BONDS to BigDecimal(0.1),
                            SubwalletType.STOCK to BigDecimal(0.4),
                        ),
                )

            val customerId = "cust_123"

            insertWalletInMemory(
                db = walletsDatabaseInMemory,
                id = "realMoneyWalletId",
                customerId = customerId,
                type = WalletType.REAL_MONEY,
                policyId = investmentPolicy.id,
            )

            insertWalletInMemory(
                db = walletsDatabaseInMemory,
                id = "investmentWalletId",
                customerId = customerId,
                type = WalletType.INVESTMENT,
                policyId = investmentPolicy.id,
            )

            insertWalletInMemory(
                db = walletsDatabaseInMemory,
                id = "emergencyFundsWalletId",
                customerId = customerId,
                type = WalletType.EMERGENCY_FUND,
                policyId = investmentPolicy.id,
            )
        }

        afterEach {
            clearMocks(ledgerServiceMock, partnerServiceMock)
            clearInMemoryDatabase(
                transactionsDatabaseInMemory = transactionsDatabaseInMemory,
                walletsDatabaseInMemory = walletsDatabaseInMemory,
            )
        }

        describe("processTransaction - deposits") {
            it("works") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.DEPOSIT,
                    )

                val transaction = transactionsService.processTransaction(request)

                transaction.status shouldBe TransactionStatus.COMPLETED

                coVerify(exactly = 1) {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = "realMoneyWalletId",
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                                CreateJournalEntry(
                                    walletId = null,
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(-100),
                                    balanceType = BalanceType.INTERNAL,
                                ),
                            ),
                    )
                }
            }

            it("fails external transaction validation") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "investmentWalletId",
                        originatorSubwalletType = SubwalletType.INVESTMENT,
                        type = TransactionType.DEPOSIT,
                    )

                shouldThrow<ExternalTransactionValidationException> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }
        }

        describe("processTransaction - withdraw") {
            it("works") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.WITHDRAW,
                    )

                val transaction = transactionsService.processTransaction(request)

                transaction.status shouldBe TransactionStatus.COMPLETED

                coVerify(exactly = 1) {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = "realMoneyWalletId",
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(-100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                                CreateJournalEntry(
                                    walletId = null,
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(100),
                                    balanceType = BalanceType.INTERNAL,
                                ),
                            ),
                    )
                }
            }

            it("fails external transaction validation") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "investmentWalletId",
                        originatorSubwalletType = SubwalletType.INVESTMENT,
                        type = TransactionType.WITHDRAW,
                    )

                shouldThrow<ExternalTransactionValidationException> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }

            it("fails insufficient funds") {
                coEvery {
                    ledgerServiceMock.getBalance(any(), any())
                } returns BigDecimal(50)

                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.WITHDRAW,
                    )

                shouldThrow<InsufficientFundsException> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }
        }

        describe("processTransaction - hold") {
            it("works") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.HOLD,
                    )

                val transaction = transactionsService.processTransaction(request)

                transaction.status shouldBe TransactionStatus.PROCESSING

                coVerify(exactly = 1) {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = "realMoneyWalletId",
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(-100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                                CreateJournalEntry(
                                    walletId = "realMoneyWalletId",
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(100),
                                    balanceType = BalanceType.HOLDING,
                                ),
                            ),
                    )
                }
            }

            it("fails if wallet is not real money or investments") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "emergencyFundsWalletId",
                        originatorSubwalletType = SubwalletType.EMERGENCY_FUND,
                        type = TransactionType.HOLD,
                    )

                shouldThrow<HoldNotAllowed> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }

            it("fails insufficient funds") {
                coEvery {
                    ledgerServiceMock.getBalance(any(), any())
                } returns BigDecimal(50)

                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.HOLD,
                    )

                shouldThrow<InsufficientFundsException> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }
        }

        describe("processTransaction - transfer") {
            it("works") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        beneficiaryWalletId = "emergencyFundsWalletId",
                        beneficiarySubwalletType = SubwalletType.EMERGENCY_FUND,
                        type = TransactionType.TRANSFER,
                    )

                val transaction = transactionsService.processTransaction(request)

                transaction.status shouldBe TransactionStatus.COMPLETED

                coVerify(exactly = 1) { partnerServiceMock.executeInternalTransfer(any()) }
                coVerify(exactly = 1) {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = "realMoneyWalletId",
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(-100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                                CreateJournalEntry(
                                    walletId = "emergencyFundsWalletId",
                                    subwalletType = SubwalletType.EMERGENCY_FUND,
                                    amount = BigDecimal(100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                            ),
                    )
                }
            }

            it("fails for invalid transfer pairs") {
                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        beneficiaryWalletId = "investmentWalletId",
                        beneficiarySubwalletType = SubwalletType.INVESTMENT,
                        type = TransactionType.TRANSFER,
                    )

                shouldThrow<TransferNotAllowed> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { partnerServiceMock.executeInternalTransfer(any()) }
                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }

            it("fails insufficient funds") {
                coEvery {
                    ledgerServiceMock.getBalance(any(), any())
                } returns BigDecimal(50)

                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        beneficiaryWalletId = "emergencyFundsWalletId",
                        beneficiarySubwalletType = SubwalletType.EMERGENCY_FUND,
                        type = TransactionType.TRANSFER,
                    )

                shouldThrow<InsufficientFundsException> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { partnerServiceMock.executeInternalTransfer(any()) }
                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }
        }

        describe("processTransaction - transfer from hold") {
            it("works") {
                val beneficiaryWallet =
                    insertWalletInMemory(
                        db = walletsDatabaseInMemory,
                        id = "investmentWalletId",
                        type = WalletType.INVESTMENT,
                        customerId = "cust_123",
                        policyId = "dummyPolicyId",
                    )

                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        beneficiaryWalletId = beneficiaryWallet.id,
                        beneficiarySubwalletType = SubwalletType.REAL_ESTATE,
                        type = TransactionType.TRANSFER_FROM_HOLD,
                    )

                val transaction = transactionsService.processTransaction(request)

                transaction.status shouldBe TransactionStatus.COMPLETED

                coVerify(exactly = 1) { partnerServiceMock.executeInternalTransfer(any()) }
                coVerify(exactly = 1) {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = "realMoneyWalletId",
                                    subwalletType = SubwalletType.REAL_MONEY,
                                    amount = BigDecimal(-100),
                                    balanceType = BalanceType.HOLDING,
                                ),
                                CreateJournalEntry(
                                    walletId = "investmentWalletId",
                                    subwalletType = SubwalletType.REAL_ESTATE,
                                    amount = BigDecimal(100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                            ),
                    )
                }
            }

            it("fails for invalid transfer pairs") {
                val beneficiaryWallet =
                    insertWalletInMemory(
                        db = walletsDatabaseInMemory,
                        id = "emergencyFundsWalletId",
                        type = WalletType.EMERGENCY_FUND,
                        customerId = "cust_123",
                        policyId = "dummyPolicyId",
                    )

                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        beneficiaryWalletId = beneficiaryWallet.id,
                        beneficiarySubwalletType = SubwalletType.EMERGENCY_FUND,
                        type = TransactionType.TRANSFER_FROM_HOLD,
                    )

                shouldThrow<TransferFromHoldNotAllowed> { transactionsService.processTransaction(request) }

                coVerify(exactly = 0) { partnerServiceMock.executeInternalTransfer(any()) }
                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }

            it("fails insufficient funds") {
                coEvery {
                    ledgerServiceMock.getBalance(any(), any())
                } returns BigDecimal(50)

                val beneficiaryWallet =
                    insertWalletInMemory(
                        db = walletsDatabaseInMemory,
                        id = "investmentWalletId",
                        type = WalletType.INVESTMENT,
                        customerId = "cust_123",
                        policyId = "dummyPolicyId",
                    )

                val request =
                    ProcessTransactionRequest(
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        beneficiaryWalletId = beneficiaryWallet.id,
                        beneficiarySubwalletType = SubwalletType.REAL_ESTATE,
                        type = TransactionType.TRANSFER_FROM_HOLD,
                    )

                shouldThrow<InsufficientFundsException> {
                    transactionsService.processTransaction(request)
                }

                coVerify(exactly = 0) { partnerServiceMock.executeInternalTransfer(any()) }
                coVerify(exactly = 0) { ledgerServiceMock.postJournalEntries(any()) }
            }
        }

        describe("reverseAndFailTransactionsBatch") {
            it("works") {
                val batchId = "batchId"
                val originatorWalletId = "originatorWalletId"

                val transaction1 =
                    insertTransactionInMemory(
                        db = transactionsDatabaseInMemory,
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey1",
                        originatorWalletId = originatorWalletId,
                        originatorSubwalletType = SubwalletType.REAL_ESTATE,
                        batchId = batchId,
                        type = TransactionType.HOLD,
                        status = TransactionStatus.PROCESSING,
                    )

                val transaction2 =
                    insertTransactionInMemory(
                        db = transactionsDatabaseInMemory,
                        amount = BigDecimal(200),
                        idempotencyKey = "idempotencyKey2",
                        originatorWalletId = originatorWalletId,
                        originatorSubwalletType = SubwalletType.STOCK,
                        batchId = batchId,
                        type = TransactionType.HOLD,
                        status = TransactionStatus.PROCESSING,
                    )

                transactionsService.reverseAndFailTransactionsBatch(batchId = batchId)

                transaction1.status shouldBe TransactionStatus.FAILED
                transaction2.status shouldBe TransactionStatus.FAILED

                coVerify {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = originatorWalletId,
                                    subwalletType = SubwalletType.REAL_ESTATE,
                                    amount = BigDecimal(100),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                                CreateJournalEntry(
                                    walletId = originatorWalletId,
                                    subwalletType = SubwalletType.REAL_ESTATE,
                                    amount = BigDecimal(-100),
                                    balanceType = BalanceType.HOLDING,
                                ),
                            ),
                    )
                }

                coVerify {
                    ledgerServiceMock.postJournalEntries(
                        journalEntries =
                            listOf(
                                CreateJournalEntry(
                                    walletId = originatorWalletId,
                                    subwalletType = SubwalletType.STOCK,
                                    amount = BigDecimal(200),
                                    balanceType = BalanceType.AVAILABLE,
                                ),
                                CreateJournalEntry(
                                    walletId = originatorWalletId,
                                    subwalletType = SubwalletType.STOCK,
                                    amount = BigDecimal(-200),
                                    balanceType = BalanceType.HOLDING,
                                ),
                            ),
                    )
                }
            }
        }

        describe("retryBatch") {
            it("works") {
                val batchId = "batchId"

                val originatingTransaction =
                    insertTransactionInMemory(
                        transactionsDatabaseInMemory,
                        id = UUID.randomUUID().toString(),
                        idempotencyKey = batchId,
                        type = TransactionType.HOLD,
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        amount = BigDecimal(300),
                        originatorWalletId = "realMoneyWalletId",
                        insertedAt = LocalDateTime.now(),
                        status = TransactionStatus.PROCESSING,
                    )

                val firstTransaction =
                    insertTransactionInMemory(
                        db = transactionsDatabaseInMemory,
                        id = UUID.randomUUID().toString(),
                        batchId = batchId,
                        idempotencyKey = "${batchId}_Stock",
                        type = TransactionType.TRANSFER_FROM_HOLD,
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        amount = BigDecimal(100),
                        originatorWalletId = "realMoneyWalletId",
                        beneficiarySubwalletType = SubwalletType.STOCK,
                        beneficiaryWalletId = "investmentWalletId",
                        insertedAt = LocalDateTime.now(),
                        status = TransactionStatus.TRANSIENT_ERROR,
                    )

                val journalEntries1 =
                    listOf(
                        CreateJournalEntry(
                            walletId = "realMoneyWalletId",
                            subwalletType = SubwalletType.REAL_MONEY,
                            balanceType = BalanceType.HOLDING,
                            amount = -BigDecimal(100),
                        ),
                        CreateJournalEntry(
                            walletId = "investmentWalletId",
                            subwalletType = SubwalletType.STOCK,
                            balanceType = BalanceType.AVAILABLE,
                            amount = BigDecimal(100),
                        ),
                    )

                insertTransactionInMemory(
                    db = transactionsDatabaseInMemory,
                    id = UUID.randomUUID().toString(),
                    batchId = batchId,
                    idempotencyKey = "${batchId}_RealEstate",
                    type = TransactionType.TRANSFER_FROM_HOLD,
                    originatorSubwalletType = SubwalletType.REAL_MONEY,
                    amount = BigDecimal(100),
                    originatorWalletId = "realMoneyWalletId",
                    beneficiarySubwalletType = SubwalletType.REAL_ESTATE,
                    beneficiaryWalletId = "investmentWalletId",
                    insertedAt = LocalDateTime.now(),
                    status = TransactionStatus.COMPLETED,
                )

                val thirdTransaction =
                    insertTransactionInMemory(
                        db = transactionsDatabaseInMemory,
                        id = UUID.randomUUID().toString(),
                        batchId = batchId,
                        idempotencyKey = "${batchId}_Cryptocurrency",
                        type = TransactionType.TRANSFER_FROM_HOLD,
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        amount = BigDecimal(100),
                        originatorWalletId = "realMoneyWalletId",
                        beneficiarySubwalletType = SubwalletType.CRYPTOCURRENCY,
                        beneficiaryWalletId = "investmentWalletId",
                        insertedAt = LocalDateTime.now(),
                        status = TransactionStatus.TRANSIENT_ERROR,
                    )

                val journalEntries3 =
                    listOf(
                        CreateJournalEntry(
                            walletId = "realMoneyWalletId",
                            subwalletType = SubwalletType.REAL_MONEY,
                            balanceType = BalanceType.HOLDING,
                            amount = -BigDecimal(100),
                        ),
                        CreateJournalEntry(
                            walletId = "investmentWalletId",
                            subwalletType = SubwalletType.CRYPTOCURRENCY,
                            balanceType = BalanceType.AVAILABLE,
                            amount = BigDecimal(100),
                        ),
                    )

                coEvery {
                    partnerServiceMock.executeInternalTransfer(any())
                } returns Unit

                coEvery {
                    ledgerServiceMock.postJournalEntries(any())
                } returns LocalDateTime.now()

                transactionsService.retryBatch(batchId, 3)

                coVerify(exactly = 2) {
                    partnerServiceMock.executeInternalTransfer(any())
                }

                coVerify {
                    ledgerServiceMock.postJournalEntries(journalEntries1)
                    ledgerServiceMock.postJournalEntries(journalEntries3)
                }

                val firstTransactionCompleted = transactionsDatabaseInMemory.find(TransactionFilter(id = firstTransaction.id))
                val thirdTransactionCompleted = transactionsDatabaseInMemory.find(TransactionFilter(id = thirdTransaction.id))
                val originatingTransactionCompleted = transactionsDatabaseInMemory.find(TransactionFilter(id = originatingTransaction.id))

                firstTransactionCompleted.first().status shouldBe TransactionStatus.COMPLETED
                thirdTransactionCompleted.first().status shouldBe TransactionStatus.COMPLETED
                originatingTransactionCompleted.first().status shouldBe TransactionStatus.COMPLETED
            }
        }
    }
}
