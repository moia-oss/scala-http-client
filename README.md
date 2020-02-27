# Scala-HTTP-Client

This is a wrapper around the akka-http-client that adds

* handling for domain errors as HTTP 400 returns
* retry logic
* deadlines
* error handling
* logging
* AWS request signing

[![CircleCI](https://circleci.com/gh/moia-dev/scala-http-client/tree/master.svg?style=svg)](https://circleci.com/gh/moia-dev/scala-http-client/tree/master)
[![Scala 2.13](https://img.shields.io/maven-central/v/io.moia/scala-http-client_2.13.svg)](https://search.maven.org/search?q=scala-http-client_2.13)

## Usage

```sbt
libraryDependencies += "io.moia" % "scala-http-client" % "1.1.0"
```

```scala
implicit val system: ActorSystem                = ActorSystem("test")
implicit val executionContext: ExecutionContext = system.dispatcher

val httpClientConfig: HttpClientConfig = HttpClientConfig("http", isSecureConnection = false, "127.0.0.1", 8888)
val clock: Clock                       = Clock.systemUTC()
val httpMetrics: HttpMetrics[NoLoggingContext]   = new HttpMetrics[NoLoggingContext] {
  override def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse)(implicit ctx: NoLoggingContext): Unit = ()
}
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

## Custom Logging

To use a custom logger (for correlation ids etc), you can use the typed `LoggingHttpClient`:

```scala
// create a context-class
case class LoggingContext(context: String)

// create a logger
implicit val canLogString: CanLog[LoggingContext]            = new CanLog[LoggingContext] // override logMessage here!
implicit val theLogger: LoggerTakingImplicit[LoggingContext] = Logger.takingImplicit(LoggerFactory.getLogger(getClass.getName))

// create the client
new LoggingHttpClient[LoggingContext](httpClientConfig, "TestGateway", typedHttpMetrics, retryConfig, clock, None)

// create an implicit logging context
implicit val ctx: LoggingContext                             = LoggingContext("Logging Context")

// make a request
testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)
```

## Publishing

You need an account on https://oss.sonatype.org that can [access](https://issues.sonatype.org/browse/OSSRH-52948) the `io.moia` group.

Add your credentials to `~/.sbt/sonatype_credential` and run
```sbt
sbt:scala-http-client> +publishSigned
```

Then head to https://oss.sonatype.org/#stagingRepositories, select the repository, `Close` and then `Release` it.
