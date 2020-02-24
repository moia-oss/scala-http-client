package io.moia.scalaHttpClient

trait NoLoggingContext
object NoLoggingContext extends NoLoggingContext {
  implicit val noLoggingContext: NoLoggingContext = NoLoggingContext
}
