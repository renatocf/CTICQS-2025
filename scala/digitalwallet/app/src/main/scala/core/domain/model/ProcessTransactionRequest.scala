package core.domain.model

import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.TransactionType.TransactionType

case class ProcessTransactionRequest(
  amount: BigDecimal,
  batchId: Option[String] = None,
  idempotencyKey: String,
  originatorWalletId: String,
  originatorSubwalletType: SubwalletType,
  beneficiaryWalletId: Option[String] = None,
  beneficiarySubwalletType: Option[SubwalletType] = None,
  transactionType: TransactionType,
  metadata: Option[TransactionMetadata] = None,
)
