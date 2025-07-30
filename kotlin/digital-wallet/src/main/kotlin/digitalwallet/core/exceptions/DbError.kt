package digitalwallet.core.exceptions

open class DbError(message: String) : Exception(message)

class TransactionNotFound(message: String) : DbError(message)