package digitalwallet.data.common.exceptions

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException

open class DbError(message: String) : Exception(message)

class StatusTransitionNotAllowed(message: String) : DbError(message)