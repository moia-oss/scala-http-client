package io.moia.scalaHttpClient

import io.moia.scalaHttpClient.AwsRequestSigner.AwsRequestSignerConfig

import scala.concurrent.duration.{FiniteDuration, _}

/**
  * Configuration of a particular HTTP client.
  *
  * @param scheme For example "http" or "https"
  * @param host Name of the host
  * @param port Port of the host
  * @param awsRequestSignerConfig Optional config for AWS request signing
  */
final case class HttpClientConfig(
    scheme: String,
    host: String,
    port: Int,
    awsRequestSignerConfig: Option[AwsRequestSignerConfig] = None
)

/**
  * Retry configuration of HTTP clients.
  *
  * @param retriesRequestTimeout Number of retries for HTTP 408
  * @param retriesTooManyRequests Number of retries for HTTP 429 (usually in combination with retry-after header)
  * @param retriesClientError Number of retries for all other 4xx codes
  * @param retriesInternalServerError Number of retries for HTTP 500
  * @param retriesBadGateway Number of retries for HTTP 502
  * @param retriesServiceUnavailable Number of retries for HTTP 503
  * @param retriesServerError Number of retries for all other 5xx codes
  * @param retriesException Number of retries for exceptions in the underlying akka-http client
  * @param initialBackoff Time to wait until the first retry. Is multiplied with 2^(# of retry).
  *                       Example:
  *                       10ms * 2^0 => 10ms
  *                       10ms * 2^1 => 20ms
  *                       10ms * 2^2 => 40ms
  *                       10ms * 2^3 => 80ms
  *                       10ms * 2^4 => 160ms
  * @param strictifyResponseTimeout Time to wait for streaming data to complete
  */
final case class RetryConfig(
    retriesRequestTimeout: Int,
    retriesTooManyRequests: Int,
    retriesClientError: Int,
    retriesInternalServerError: Int,
    retriesBadGateway: Int,
    retriesServiceUnavailable: Int,
    retriesServerError: Int,
    retriesException: Int,
    initialBackoff: FiniteDuration,
    strictifyResponseTimeout: FiniteDuration
)

object RetryConfig {
  val default: RetryConfig = RetryConfig(
    retriesRequestTimeout      = 1,
    retriesTooManyRequests     = 2,
    retriesClientError         = 0,
    retriesInternalServerError = 3,
    retriesBadGateway          = 3,
    retriesServiceUnavailable  = 3,
    retriesServerError         = 3,
    retriesException           = 3,
    initialBackoff             = 10.millis,
    strictifyResponseTimeout   = 1.second
  )
}
