package io.moia.scalaHttpClient

import io.moia.scalaHttpClient.AwsRequestSigner.AwsRequestSignerConfig

import scala.concurrent.duration.{FiniteDuration, _}

/**
  * Configuration of a particular HTTP client.
  */
final case class HttpClientConfig(
    scheme: String,
    isSecureConnection: Boolean,
    host: String,
    port: Int,
    awsRequestSignerConfig: Option[AwsRequestSignerConfig] = None,
    defaultDeadline: Option[FiniteDuration]                = None
)

/**
  * Retry configuration of HTTP clients.
  */
final case class RetryConfig(
    retriesTooManyRequests: Int,
    retriesServiceUnavailable: Int,
    retriesRequestTimeout: Int,
    retriesServerError: Int,
    retriesException: Int,
    initialBackoff: FiniteDuration,
    strictifyResponseTimeout: FiniteDuration
)

object RetryConfig {
  val default: RetryConfig = RetryConfig(
    retriesTooManyRequests    = 2,
    retriesServiceUnavailable = 3,
    retriesRequestTimeout     = 1,
    retriesServerError        = 3,
    retriesException          = 3,
    initialBackoff            = 10.millis,
    strictifyResponseTimeout  = 1.second
  )
}
