package digitalwallet

import digitalwallet.adapters.InvestmentPolicyInMemoryDatabase
import digitalwallet.adapters.TransactionsInMemoryDatabase
import digitalwallet.adapters.WalletsInMemoryDatabase
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.ProcessTransactionRequest
import digitalwallet.core.services.InvestmentService
import digitalwallet.core.services.LedgerService
import digitalwallet.core.services.TransactionsService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal

@MicronautTest
class InvestmentServiceTest : DescribeSpec() {
    private val transactionsServiceMock: TransactionsService = mockk()
    private val ledgerServiceMock: LedgerService = mockk()

    private val transactionsInMemoryDatabase = TransactionsInMemoryDatabase()
    private val investmentPolicyInMemoryDatabase = InvestmentPolicyInMemoryDatabase()
    private val walletsInMemoryDatabase = WalletsInMemoryDatabase()

    private val investmentService =
        InvestmentService(
            transactionsInMemoryDatabase,
            walletsInMemoryDatabase,
            investmentPolicyInMemoryDatabase,
            transactionsServiceMock,
            ledgerServiceMock,
        )

    init {
        beforeEach {
            val investmentPolicy1 =
                insertInvestmentPolicyInMemory(
                    db = investmentPolicyInMemoryDatabase,
                    id = "policyId1",
                    allocationStrategy =
                        mutableMapOf(
                            SubwalletType.REAL_ESTATE to BigDecimal(0.25),
                            SubwalletType.CRYPTOCURRENCY to BigDecimal(0.25),
                            SubwalletType.BONDS to BigDecimal(0.25),
                            SubwalletType.STOCK to BigDecimal(0.25),
                        ),
                )

            val customerId1 = "cust_123"

            insertWalletInMemory(
                db = walletsInMemoryDatabase,
                id = "realMoneyWalletId1",
                customerId = customerId1,
                type = WalletType.REAL_MONEY,
                policyId = investmentPolicy1.id,
            )

            insertWalletInMemory(
                db = walletsInMemoryDatabase,
                id = "investmentWalletId1",
                customerId = customerId1,
                type = WalletType.INVESTMENT,
                policyId = investmentPolicy1.id,
            )

            val investmentPolicy2 =
                insertInvestmentPolicyInMemory(
                    db = investmentPolicyInMemoryDatabase,
                    id = "policyId2",
                    allocationStrategy =
                        mutableMapOf(
                            SubwalletType.REAL_ESTATE to BigDecimal(0.5),
                            SubwalletType.CRYPTOCURRENCY to BigDecimal(0.0),
                            SubwalletType.BONDS to BigDecimal(0.0),
                            SubwalletType.STOCK to BigDecimal(0.5),
                        ),
                )

            val customerId2 = "cust_456"

            insertWalletInMemory(
                db = walletsInMemoryDatabase,
                id = "realMoneyWalletId2",
                customerId = customerId2,
                type = WalletType.REAL_MONEY,
                policyId = investmentPolicy2.id,
            )

            insertWalletInMemory(
                db = walletsInMemoryDatabase,
                id = "investmentWalletId2",
                customerId = customerId2,
                type = WalletType.INVESTMENT,
                policyId = investmentPolicy2.id,
            )
        }

        afterEach {
            clearMocks(transactionsServiceMock, ledgerServiceMock)
            clearInMemoryDatabase(
                transactionsDatabaseInMemory = transactionsInMemoryDatabase,
                walletsDatabaseInMemory = walletsInMemoryDatabase,
                investmentPolicyDatabaseInMemory = investmentPolicyInMemoryDatabase,
            )
        }

        describe("buyFunds") {
            it("works") {
                val transaction1 =
                    insertTransactionInMemory(
                        db = transactionsInMemoryDatabase,
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey1",
                        originatorWalletId = "realMoneyWalletId1",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.HOLD,
                        status = TransactionStatus.PROCESSING,
                    )

                val transaction2 =
                    insertTransactionInMemory(
                        db = transactionsInMemoryDatabase,
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey2",
                        originatorWalletId = "realMoneyWalletId2",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        type = TransactionType.HOLD,
                        status = TransactionStatus.PROCESSING,
                    )

                coEvery {
                    transactionsServiceMock.processTransaction(any())
                } returns insertTransactionInMemory(db = transactionsInMemoryDatabase, status = TransactionStatus.COMPLETED).toModel()

                investmentService.buyFunds()

                transaction1.status shouldBe TransactionStatus.COMPLETED
                transaction2.status shouldBe TransactionStatus.COMPLETED

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal("25.00"),
                                batchId = transaction1.id,
                                idempotencyKey = "${transaction1.id}_${SubwalletType.REAL_ESTATE}",
                                originatorWalletId = "realMoneyWalletId1",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                beneficiaryWalletId = "investmentWalletId1",
                                beneficiarySubwalletType = SubwalletType.REAL_ESTATE,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                                metadata = null,
                            ),
                    )
                }

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal("25.00"),
                                batchId = transaction1.id,
                                idempotencyKey = "${transaction1.id}_${SubwalletType.STOCK}",
                                originatorWalletId = "realMoneyWalletId1",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                beneficiaryWalletId = "investmentWalletId1",
                                beneficiarySubwalletType = SubwalletType.STOCK,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                            ),
                    )
                }

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal("25.00"),
                                batchId = transaction1.id,
                                idempotencyKey = "${transaction1.id}_${SubwalletType.BONDS}",
                                originatorWalletId = "realMoneyWalletId1",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                beneficiaryWalletId = "investmentWalletId1",
                                beneficiarySubwalletType = SubwalletType.BONDS,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                            ),
                    )
                }

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal("25.00"),
                                batchId = transaction1.id,
                                idempotencyKey = "${transaction1.id}_${SubwalletType.CRYPTOCURRENCY}",
                                originatorWalletId = "realMoneyWalletId1",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                beneficiaryWalletId = "investmentWalletId1",
                                beneficiarySubwalletType = SubwalletType.CRYPTOCURRENCY,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                            ),
                    )
                }

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal("50.0"),
                                batchId = transaction2.id,
                                idempotencyKey = "${transaction2.id}_${SubwalletType.REAL_ESTATE}",
                                originatorWalletId = "realMoneyWalletId2",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                beneficiaryWalletId = "investmentWalletId2",
                                beneficiarySubwalletType = SubwalletType.REAL_ESTATE,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                            ),
                    )
                }

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal("50.0"),
                                batchId = transaction2.id,
                                idempotencyKey = "${transaction2.id}_${SubwalletType.STOCK}",
                                originatorWalletId = "realMoneyWalletId2",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                beneficiaryWalletId = "investmentWalletId2",
                                beneficiarySubwalletType = SubwalletType.STOCK,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                            ),
                    )
                }
            }
        }

        describe("sellFunds") {
            it("works") {
                val transaction1 =
                    insertTransactionInMemory(
                        db = transactionsInMemoryDatabase,
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey1",
                        originatorWalletId = "investmentWalletId1",
                        originatorSubwalletType = SubwalletType.STOCK,
                        type = TransactionType.HOLD,
                        status = TransactionStatus.PROCESSING,
                    )

                val transaction2 =
                    insertTransactionInMemory(
                        db = transactionsInMemoryDatabase,
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey2",
                        originatorWalletId = "investmentWalletId2",
                        originatorSubwalletType = SubwalletType.REAL_ESTATE,
                        type = TransactionType.HOLD,
                        status = TransactionStatus.PROCESSING,
                    )

                coEvery {
                    transactionsServiceMock.processTransaction(any())
                } returns insertTransactionInMemory(db = transactionsInMemoryDatabase, status = TransactionStatus.COMPLETED).toModel()

                investmentService.sellFunds()

                transaction1.status shouldBe TransactionStatus.COMPLETED
                transaction2.status shouldBe TransactionStatus.COMPLETED

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = transaction1.id,
                                originatorWalletId = "investmentWalletId1",
                                originatorSubwalletType = SubwalletType.STOCK,
                                beneficiaryWalletId = "realMoneyWalletId1",
                                beneficiarySubwalletType = SubwalletType.REAL_MONEY,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                                metadata = null,
                            ),
                    )
                }

                coVerify {
                    transactionsServiceMock.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = transaction2.id,
                                originatorWalletId = "investmentWalletId2",
                                originatorSubwalletType = SubwalletType.REAL_ESTATE,
                                beneficiaryWalletId = "realMoneyWalletId2",
                                beneficiarySubwalletType = SubwalletType.REAL_MONEY,
                                type = TransactionType.TRANSFER_FROM_HOLD,
                                metadata = null,
                            ),
                    )
                }
            }
        }
    }
}
