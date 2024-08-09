package digitalwallet.data.common

data class BankAccountIdentification(
    val bankCode: String,
    val branchCode: String,
    val accountNumber: String,
)

data class EntityInfo(
    val name: String?,
    val bankAccount: BankAccountIdentification
)

data class TransactionMetadata(
    val counterparty: EntityInfo?,
)