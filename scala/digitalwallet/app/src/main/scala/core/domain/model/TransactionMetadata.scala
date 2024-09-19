package core.domain.model

case class BankAccountIdentification(
  bankCode: String,
  branchCode: String,
  accountNumber: String,
)

case class EntityInfo(
  name: Option[String],
  bankAccount: BankAccountIdentification,
)

case class TransactionMetadata(
  counterparty: Option[EntityInfo]
)
