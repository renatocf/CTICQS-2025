package core.services

import core.domain.entities.Transaction
import core.errors.PartnerError

class PartnerService {
  def executeInternalTransfer(transaction: Transaction): Either[PartnerError, Unit] = {
      println(s"Executing internal transfer for transaction: ${transaction.id}")

      Right(())
  }
}
