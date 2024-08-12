package digitalwallet.data.common.exceptions

open class DbError(message: String) : Exception(message)

class StatusTransitionNotAllowed(message: String) : DbError(message)