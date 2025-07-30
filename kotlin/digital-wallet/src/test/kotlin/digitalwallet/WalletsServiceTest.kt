package digitalwallet

import digitalwallet.adapters.InvestmentPolicyInMemoryDatabase
import digitalwallet.adapters.WalletsInMemoryDatabase
import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionStatus
import digitalwallet.core.domain.enums.TransactionType
import digitalwallet.core.domain.enums.WalletType
import digitalwallet.core.domain.models.*
import digitalwallet.core.exceptions.InsufficientFundsException
import digitalwallet.core.exceptions.PartnerException
import digitalwallet.core.exceptions.TransactionFailed
import digitalwallet.core.services.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDateTime

@MicronautTest
class WalletsServiceTest : DescribeSpec() {
    private val transactionsService: TransactionsService = mockk()
    private val investmentService: InvestmentService = mockk()

    private val investmentPolicyInMemoryDatabase = InvestmentPolicyInMemoryDatabase()
    private val walletsDatabaseInMemory = WalletsInMemoryDatabase()

    private val walletsService =
        WalletsService(walletsDatabaseInMemory, investmentPolicyInMemoryDatabase, transactionsService, investmentService)

    init {
        beforeEach {
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
            clearMocks(transactionsService, investmentService)
            clearInMemoryDatabase(
                walletsDatabaseInMemory = walletsDatabaseInMemory,
                investmentPolicyDatabaseInMemory = investmentPolicyInMemoryDatabase,
            )
        }

        describe("invest") {
            it("works") {
                val hold =
                    Hold(
                        id = "transactionId",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        status = TransactionStatus.PROCESSING,
                        insertedAt = LocalDateTime.now(),
                    )

                coEvery {
                    transactionsService.processTransaction(any())
                } returns hold

                val request =
                    InvestmentRequest(
                        customerId = "cust_123",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                    )

                walletsService.invest(request)

                coVerify {
                    transactionsService.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = "idempotencyKey",
                                originatorWalletId = "realMoneyWalletId",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                type = TransactionType.HOLD,
                            ),
                    )
                }
            }

            it("fail transaction if validation failed") {
                val hold =
                    Hold(
                        id = "transactionId",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        status = TransactionStatus.FAILED,
                        insertedAt = LocalDateTime.now(),
                    )

                val failedHold =
                    Hold(
                        id = hold.id,
                        amount = hold.amount,
                        idempotencyKey = hold.idempotencyKey,
                        originatorWalletId = hold.originatorWalletId,
                        originatorSubwalletType = hold.originatorSubwalletType,
                        status = TransactionStatus.FAILED,
                        insertedAt = hold.insertedAt,
                    )

                coEvery {
                    transactionsService.processTransaction(any())
                } throws InsufficientFundsException("message")

                coEvery {
                    transactionsService.handleException(any(), any(), any())
                } returns failedHold

                val request =
                    InvestmentRequest(
                        customerId = "cust_123",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                    )

                shouldThrow<InvestmentFailed> {
                    walletsService.invest(request)
                }

                coVerify(exactly = 1) {
                    transactionsService.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = "idempotencyKey",
                                originatorWalletId = "realMoneyWalletId",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                type = TransactionType.HOLD,
                            ),
                    )
                }

                coVerify(exactly = 1) {
                    transactionsService.handleException(
                        any(),
                        TransactionStatus.FAILED,
                        hold.idempotencyKey,
                    )
                }
            }

            it("fail transaction if call to partner failed") {
                val hold =
                    Hold(
                        id = "transactionId",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                        originatorWalletId = "realMoneyWalletId",
                        originatorSubwalletType = SubwalletType.REAL_MONEY,
                        status = TransactionStatus.FAILED,
                        insertedAt = LocalDateTime.now(),
                    )

                val failedHold =
                    Hold(
                        id = hold.id,
                        amount = hold.amount,
                        idempotencyKey = hold.idempotencyKey,
                        originatorWalletId = hold.originatorWalletId,
                        originatorSubwalletType = hold.originatorSubwalletType,
                        status = TransactionStatus.FAILED,
                        insertedAt = hold.insertedAt,
                    )

                coEvery {
                    transactionsService.processTransaction(any())
                } throws PartnerException("message")

                coEvery {
                    transactionsService.handleException(any(), any(), any())
                } returns failedHold

                val request =
                    InvestmentRequest(
                        customerId = "cust_123",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                    )

                walletsService.invest(request)

                coVerify(exactly = 1) {
                    transactionsService.processTransaction(
                        request =
                            ProcessTransactionRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = "idempotencyKey",
                                originatorWalletId = "realMoneyWalletId",
                                originatorSubwalletType = SubwalletType.REAL_MONEY,
                                type = TransactionType.HOLD,
                            ),
                    )
                }

                coVerify(exactly = 1) {
                    transactionsService.handleException(
                        any(),
                        TransactionStatus.TRANSIENT_ERROR,
                        hold.idempotencyKey,
                    )
                }
            }
        }

        describe("liquidate") {
            it("works") {
                val request =
                    LiquidationRequest(
                        customerId = "cust_123",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                    )

                coEvery {
                    investmentService.executeMovementWithInvestmentPolicy(any())
                } returns Unit

                walletsService.liquidate(request)

                coVerify(exactly = 1) {
                    investmentService.executeMovementWithInvestmentPolicy(
                        request =
                            InvestmentMovementRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = "idempotencyKey",
                                walletId = "investmentWalletId",
                                transactionType = TransactionType.HOLD,
                                investmentPolicy =
                                    InvestmentPolicy(
                                        id = "policyId",
                                        allocationStrategy =
                                            mutableMapOf(
                                                SubwalletType.REAL_ESTATE to BigDecimal(0.4),
                                                SubwalletType.CRYPTOCURRENCY to BigDecimal(0.1),
                                                SubwalletType.BONDS to BigDecimal(0.1),
                                                SubwalletType.STOCK to BigDecimal(0.4),
                                            ),
                                    ),
                            ),
                    )
                }
            }

            it("fails if holdWithPolicy throws an exception") {
                val request =
                    LiquidationRequest(
                        customerId = "cust_123",
                        amount = BigDecimal(100),
                        idempotencyKey = "idempotencyKey",
                    )

                coEvery {
                    investmentService.executeMovementWithInvestmentPolicy(any())
                } throws TransactionFailed("")

                shouldThrow<LiquidationFailed> {
                    walletsService.liquidate(request)
                }

                coVerify(exactly = 1) {
                    investmentService.executeMovementWithInvestmentPolicy(
                        request =
                            InvestmentMovementRequest(
                                amount = BigDecimal(100),
                                idempotencyKey = "idempotencyKey",
                                walletId = "investmentWalletId",
                                transactionType = TransactionType.HOLD,
                                investmentPolicy =
                                    InvestmentPolicy(
                                        id = "policyId",
                                        allocationStrategy =
                                            mutableMapOf(
                                                SubwalletType.REAL_ESTATE to BigDecimal(0.4),
                                                SubwalletType.CRYPTOCURRENCY to BigDecimal(0.1),
                                                SubwalletType.BONDS to BigDecimal(0.1),
                                                SubwalletType.STOCK to BigDecimal(0.4),
                                            ),
                                    ),
                            ),
                    )
                }
            }
        }
    }
}
