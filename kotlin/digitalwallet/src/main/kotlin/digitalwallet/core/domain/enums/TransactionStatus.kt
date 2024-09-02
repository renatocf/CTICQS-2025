package digitalwallet.core.domain.enums

enum class TransactionStatus {
    CREATING,
    PROCESSING,
    FAILED,
    TRANSIENT_ERROR,
    COMPLETED
}