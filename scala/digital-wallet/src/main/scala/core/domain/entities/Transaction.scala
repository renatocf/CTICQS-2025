package core.domain.entities

import java.time.LocalDateTime
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.TransactionStatus.TransactionStatus
import core.domain.enums.TransactionType.TransactionType
import core.domain.model.TransactionMetadata

case class Transaction(
    id: String,
    batchId: Option[String] = None,
    amount: BigDecimal,
    idempotencyKey: String,
    originatorWalletId: String,
    originatorSubwalletType: SubwalletType,
    beneficiaryWalletId: Option[String] = None,
    beneficiarySubwalletType: Option[SubwalletType] = None,
    transactionType: TransactionType,
    insertedAt: LocalDateTime,
    var completedAt: Option[LocalDateTime] = None,
    var failedAt: Option[LocalDateTime] = None,
    var status: TransactionStatus,
    var statusReason: Option[String] = None,
)
