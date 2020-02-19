# Scala-HTTP-Client

This is a wrapper around the akka-http-client that adds

* handling for domain errors as HTTP 400 returns
* retry logic
* deadlines
* error handling
* logging
* AWS request signing

[![CircleCI](https://circleci.com/gh/moia-dev/scala-http-client/tree/master.svg?style=svg)](https://circleci.com/gh/moia-dev/scala-http-client/tree/master)

## Usage

```sbt
libraryDependencies += "io.moia" % "scala-http-client" % "1.0.0"
```

```scala
implicit val system: ActorSystem                = ActorSystem("test")
implicit val executionContext: ExecutionContext = system.dispatcher

val httpClientConfig: HttpClientConfig = HttpClientConfig("http", isSecureConnection = false, "127.0.0.1", 8888)
val clock: Clock                       = Clock.systemUTC()
val httpMetrics: HttpMetrics           = (_: HttpMethod, _: Uri.Path, _: HttpResponse) => ()
val retryConfig: RetryConfig =
    RetryConfig(
      retriesTooManyRequests = 2,
      retriesServiceUnavailable = 3,
      retriesRequestTimeout = 1,
      retriesServerError = 3,
      retriesException = 3,
      initialBackoff = 10.millis,
      strictifyResponseTimeout = 1.second
    )
val awsRequestSigner: AwsRequestSigner = AwsRequestSigner.fromConfig(AwsRequestSignerConfig.BasicCredentials("example", "secret-key", "eu-central-1"))

final class CustomHeader(id: String) extends ModeledCustomHeader[CustomHeader] {
  override def renderInRequests(): Boolean                           = true
  override def renderInResponses(): Boolean                          = true
  override def companion: ModeledCustomHeaderCompanion[CustomHeader] = CustomHeader
  override def value(): String                                       = id
}
object CustomHeader extends ModeledCustomHeaderCompanion[CustomHeader] {
  override def name: String                            = "custom-header"
  override def parse(value: String): Try[CustomHeader] = Try(new CustomHeader(value))
}
val httpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, Some(awsRequestSigner))

val response = testHttpClient.request(HttpMethods.POST, HttpEntity.apply("Example"), "/test", immutable.Seq(new CustomHeader("foobar")), Deadline.now + 10.seconds)

response.flatMap {
  case HttpClientSuccess(content) => Unmarshal(content).to[MySuccessObject].map(Right(_))
  case DomainError(content)       => Unmarshal(content).to[DomainErrorObject].map(Left(_))
  case failure: HttpClientFailure => throw GatewayException(failure.toString)
}
```