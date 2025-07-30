package core.services

import core.domain.entities.Transaction
import core.domain.enums.SubwalletType.SubwalletType
import core.domain.enums.WalletType.WalletType
import core.domain.enums.{BalanceType, SubwalletType, TransactionType, WalletType}
import core.domain.model.LedgerQuery
import core.errors.{TransactionValidationError, *}
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
      _ <- validateOriginatorSubwalletType(
        transaction.originatorSubwalletType, 
        List(
          SubwalletType.RealMoney, 
          SubwalletType.Stock, 
          SubwalletType.Bonds, 
          SubwalletType.RealEstate, 
          SubwalletType.Cryptocurrency)
      )
      _ <- validateBalance(transaction.originatorWalletId, transaction.amount)
    } yield ()
  }

  private def validateTransfer(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    for {
      beneficiarySubwalletType <- transaction.beneficiarySubwalletType
        .toRight(TransactionValidationFailed(s"Transfer ${transaction.id} must contain beneficiaryWalletId"))

      _ <- validateTransferBetweenSubwallets(
        transaction.originatorSubwalletType,
        beneficiarySubwalletType,
        Set((SubwalletType.RealMoney, SubwalletType.EmergencyFunds), (SubwalletType.EmergencyFunds, SubwalletType.RealMoney))
      )
      
      _ <- validateBalance(transaction.originatorWalletId, transaction.amount)
    } yield ()
  }

  private def validateTransferFromHold(transaction: Transaction): Either[TransactionValidationError, Unit] = {
    for {
      originatorWallet <- walletsRepo.findById(transaction.originatorWalletId)
        .toRight(TransactionValidationFailed(s"Wallet ${transaction.originatorWalletId} not found"))

      beneficiaryWalletId <- transaction.beneficiaryWalletId
        .toRight(TransactionValidationFailed(s"Transfer from hold ${transaction.id} must contain beneficiaryWalletId"))

      beneficiaryWallet <- walletsRepo.findById(beneficiaryWalletId)
        .toRight(TransactionValidationFailed(s"Wallet ${beneficiaryWalletId} not found"))

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
        .toRight(TransactionValidationFailed(s"Wallet $originatorWalletId not found"))

      balance = walletsService.getAvailableBalance(wallet)

      result <- if (amount > balance) {
        Left(InsufficientFundsValidationError(s"Wallet $originatorWalletId has no sufficient funds"))
      } else {
        Right(())
      }
    } yield ()

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
    val originatorSubwalletPendingBalance = walletsService.getSubwalletPendingBalance(walletId, subwalletType)

    if (amount <= originatorSubwalletPendingBalance) Right(())
    else Left(InsufficientFundsValidationError(s"Wallet ${walletId} has no sufficient funds"))
  }
}
