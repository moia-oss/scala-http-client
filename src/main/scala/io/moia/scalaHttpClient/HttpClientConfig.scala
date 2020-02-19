package io.moia.scalaHttpClient

import AwsRequestSigner.AwsRequestSignerConfig

import scala.concurrent.duration.FiniteDuration

/**
  * Configuration of a particular HTTP client.
  */
final case class HttpClientConfig(
    scheme: String,
    isSecureConnection: Boolean,
    host: String,
    port: Int,
    awsRequestSignerConfig: Option[AwsRequestSignerConfig] = None,
    defaultDeadline: Option[FiniteDuration] = None
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
