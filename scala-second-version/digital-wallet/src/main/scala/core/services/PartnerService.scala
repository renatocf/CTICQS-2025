package core.services

import core.domain.entities.Transaction
import core.errors.PartnerServiceError

class PartnerService {
  def executeInternalTransfer(transaction: Transaction): Either[PartnerServiceError, Unit] = {
      println(s"Executing internal transfer for transaction: ${transaction.id}")

      Right(())
  }
}
