package digitalwallet.data.common.exceptions

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException

class DepositNotAllowed(message: String) : HttpStatusException(HttpStatus.PRECONDITION_FAILED, message)