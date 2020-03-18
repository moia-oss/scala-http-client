package io.moia.scalaHttpClient

import org.scalatest.Assertions

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}
import scala.concurrent.{Await, Future, TimeoutException}

/**
  * This is a helper similar to `OptionValues`.
  * It awaits a `Future` value within an accepted duration.
  * That is much nicer that `ScalaFutures` with their stupid patience config!
  *
  * Example:
  *
  * ```
  * val x: Future[Y] = ???
  *
  * val y: Y = x.futureValue // <- blocks until the value is present, fails after future failed or the timeout
  * ```
  */
trait FutureValues extends Assertions {
  protected implicit val defaultAwaitDuration: FiniteDuration = 1000.millis

  implicit class WithFutureValue[T](future: Future[T]) {
    def futureValue(implicit awaitDuration: Duration): T =
      try {
        Await.result(future, awaitDuration)
      } catch {
        case _: TimeoutException =>
          fail(s"Future timeout out after $awaitDuration.")
      }

    def isReadyWithin(duration: Duration): Boolean =
      try {
        Await.result(future, duration)
        true
      } catch {
        case _: TimeoutException => false
      }
  }
}
