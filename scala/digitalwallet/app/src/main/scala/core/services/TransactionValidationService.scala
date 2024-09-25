package core.services

import core.domain.entities.Transaction
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.{BalanceType, SubwalletType, TransactionType, WalletType}
import core.domain.enums.WalletType.WalletType
import core.domain.model.LedgerQuery
import core.errors.{InsufficientFundsValidationError, InvalidArgument, OriginatorSubwalletTypeValidationError, StatusTransitionNotAllowed, TransactionError, TransactionValidationError, TransferBetweenSubwalletsValidationError, TransferBetweenWalletsValidationError}
import ports.WalletsDatabase

class TransactionValidationService(walletsRepo: WalletsDatabase, walletsService: WalletsService, ledgerService: LedgerService) {
  def validateTransaction(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    transaction.transactionType match {
      case TransactionType.Deposit => validateDeposit(transaction)
      case TransactionType.Withdraw => validateWithdraw(transaction)
      case TransactionType.Hold => validateHold(transaction)
      case TransactionType.Transfer => validateTransfer(transaction)
      case TransactionType.TransferFromHold => validateTransferFromHold(transaction)
    }
  }

  private def validateDeposit(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    validateOriginatorSubwalletType(transaction.originatorSubwalletType, List(SubwalletType.RealMoney))
  }

  private def validateWithdraw(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    for {
      _ <- validateOriginatorSubwalletType(transaction.originatorSubwalletType, List(SubwalletType.RealMoney))
      _ <- validateBalance(transaction.originatorWalletId, transaction.amount)
    } yield ()
  }

  private def validateHold(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    for {
      _ <- validateOriginatorSubwalletType(transaction.originatorSubwalletType, List(SubwalletType.RealMoney, SubwalletType.Investment))
      _ <- validateBalance(transaction.originatorWalletId, transaction.amount)
    } yield ()
  }

  private def validateTransfer(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    for {
      beneficiarySubwalletType <- transaction.beneficiarySubwalletType
        .toRight(InvalidArgument(s"Transfer from hold ${transaction.id} must contain beneficiaryWalletId"))

      _ <- validateTransferBetweenSubwallets(
        transaction.originatorSubwalletType,
        beneficiarySubwalletType,
        Set((SubwalletType.RealMoney, SubwalletType.EmergencyFunds), (SubwalletType.EmergencyFunds, SubwalletType.RealMoney))
      )
    } yield ()
  }

  private def validateTransferFromHold(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    for {
      originatorWallet <- walletsRepo.findById(transaction.originatorWalletId)
        .toRight(InvalidArgument(s"Wallet ${transaction.originatorWalletId} not found"))

      beneficiaryWalletId <- transaction.beneficiaryWalletId
        .toRight(InvalidArgument(s"Transfer from hold ${transaction.id} must contain beneficiaryWalletId"))

      beneficiaryWallet <- walletsRepo.findById(beneficiaryWalletId)
        .toRight(InvalidArgument(s"Wallet ${transaction.beneficiaryWalletId} not found"))

      _ <- validateTransferBetweenWallets(
        originatorWallet.walletType,
        beneficiaryWallet.walletType,
        Set((WalletType.RealMoney, WalletType.Investment), (WalletType.Investment, WalletType.RealMoney))
      )

      _ <- validatePendingBalance(originatorWallet.id, transaction.originatorSubwalletType, transaction.amount)
    } yield ()
  }

  private def validateOriginatorSubwalletType(originatorSubwalletType: SubwalletType, validSubwalletTypes: List[SubwalletType]): Either[TransactionValidationError, Unit] =
    if (!validSubwalletTypes.contains(originatorSubwalletType)) {
      Left(OriginatorSubwalletTypeValidationError(s"External transaction not allowed on $originatorSubwalletType type"))
    } else {
      Right(())
    }

  private def validateBalance(originatorWalletId: String, amount: BigDecimal): Either[TransactionValidationError, Unit] =
    for {
      wallet <- walletsRepo.findById(originatorWalletId)
        .toRight(InvalidArgument(s"Wallet $originatorWalletId not found"))
    } yield {
      val balance = walletsService.getAvailableBalance(wallet)
      if (amount > balance) {
        Left(InsufficientFundsValidationError(s"Wallet $originatorWalletId has no sufficient funds"))
      } else {
        Right(())
      }
    }

  private def validateTransferBetweenSubwallets(originatorSubwalletType: SubwalletType, beneficiarySubwalletType: SubwalletType, validTransferPairs: Set[(SubwalletType, SubwalletType)]): Either[TransactionValidationError, Unit] = {
    if (validTransferPairs.contains((originatorSubwalletType, beneficiarySubwalletType))) {
      Right(())
    } else {
      Left(TransferBetweenSubwalletsValidationError(s"Transfer not allowed between $originatorSubwalletType and $beneficiarySubwalletType types"))
    }
  }

  private def validateTransferBetweenWallets(originatorWalletType: WalletType, beneficiaryWalletType: WalletType, validTransferPairs: Set[(WalletType, WalletType)]): Either[TransactionValidationError, Unit] = {
    if (validTransferPairs.contains((originatorWalletType, beneficiaryWalletType))) {
      Right(())
    } else {
      Left(TransferBetweenWalletsValidationError(s"Transfer not allowed between $originatorWalletType and $beneficiaryWalletType types"))
    }
  }

  private def validatePendingBalance(walletId: String, subwalletType: SubwalletType, amount: BigDecimal): Either[TransactionValidationError, Unit] = {
    val originatorSubwalletPendingBalance = ledgerService.getBalance(
      walletId,
      List(
        LedgerQuery(
          subwalletType = subwalletType,
          balanceType = BalanceType.Holding
        )
      )
    )

    if (amount <= originatorSubwalletPendingBalance) Right(())
    else Left(InsufficientFundsValidationError("Wallet has no sufficient funds"))
  }
}
