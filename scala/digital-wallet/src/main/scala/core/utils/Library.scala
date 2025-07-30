package core.utils

import adapters.Logger

import scala.annotation.tailrec

class Library {
  private val logger = Logger()
  
  def retry[A, B](f: () => Either[A, B], n: Int): Either[Unit, B] = {
    require(n > 0, "n must be greater than 0")

    @tailrec
    def attempt(attemptsLeft: Int): Either[Unit, B] = {
      f() match {
        case Right(result) => Right(result)
        case Left(_) if attemptsLeft > 1 => attempt(attemptsLeft - 1)
        case Left(_) => Left(())
      }
    }

    attempt(n)
  }

  def maybeLogError[A, B](f: () => Either[A, B]): Either[A, B] = {
    f() match {
      case Left(e) =>
        logger.error(e.toString)
        Left(e)
      case Right(result) => Right(result)
    }
  }
}
