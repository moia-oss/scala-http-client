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
libraryDependencies += "io.moia" % "scala-http-client" % "1.2.0"
```

```scala
// create the client
val httpClient = new HttpClient(
  config           = HttpClientConfig("http", isSecureConnection = false, "127.0.0.1", 8888),
  name             = "TestClient",
  httpMetrics      = HttpMetrics.none,
  retryConfig      = RetryConfig.default,
  clock            = Clock.systemUTC(),
  awsRequestSigner = None
)
// make a request
val response: Future[HttpClientResponse] = httpClient.request(
  method   = HttpMethods.POST,
  entity   = HttpEntity.apply("Example"),
  path     = "/test",
  headers  = immutable.Seq.empty,
  deadline = Deadline.now + 10.seconds
)
// map the response to your model
response.flatMap {
  case HttpClientSuccess(content) => Unmarshal(content).to[MySuccessObject].map(Right(_))
  case DomainError(content)       => Unmarshal(content).to[DomainErrorObject].map(Left(_))
  case failure: HttpClientFailure => throw GatewayException(failure.toString)
}
```

See [SimpleExample.scala](/blob/master/src/it/scala/io/moia/scalaHttpClient/SimpleExample.scala) for a complete example.

## Custom Logging

To use a custom logger (for correlation ids etc), you can use the typed `LoggingHttpClient`. 
First create a custom `LoggerTakingImplicit`:

```scala
import com.typesafe.scalalogging._
import org.slf4j.LoggerFactory

object CustomLogging {
  case class LoggingContext(context: String)

  implicit val canLogString: CanLog[LoggingContext] = new CanLog[LoggingContext] {
    override def logMessage(originalMsg: String, ctx: LoggingContext): String = ???
    override def afterLog(ctx: LoggingContext): Unit                          = ???
  }

  val theLogger: LoggerTakingImplicit[LoggingContext] = Logger.takingImplicit(LoggerFactory.getLogger(getClass.getName))
}
``` 

Then create a `LoggingHttpClient` typed to the `LoggingContext`:

```scala
// create the client
val httpClient = new LoggingHttpClient[LoggingContext](
  config           = HttpClientConfig("http", isSecureConnection = false, "127.0.0.1", 8888),
  name             = "TestClient",
  httpMetrics      = HttpMetrics.none[LoggingContext],
  retryConfig      = RetryConfig.default,
  clock            = Clock.systemUTC(),
  logger           = CustomLogging.theLogger,
  awsRequestSigner = None
)

// create an implicit logging context
implicit val ctx: LoggingContext = LoggingContext("Logging Context")

// make a request
httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)
```

The `request` function will use the `ctx` implicitly.

See [LoggingExample.scala](/blob/master/src/it/scala/io/moia/scalaHttpClient/LoggingExample.scala) for a complete example.


## Custom Headers

To use custom-defined headers, you can extend `ModeledCustomHeader` from `akka.http.scaladsl.model.headers`:

```scala
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import scala.util.Try

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
```

Then simply send them in the request:

```scala
val response: Future[HttpClientResponse] = httpClient.request(
  method   = HttpMethods.POST,
  entity   = HttpEntity.apply("Example"),
  path     = "/test",
  headers  = immutable.Seq(new CustomHeader("foobar")),
  deadline = Deadline.now + 10.seconds
)
```

Note: If you want to access the headers from the _response_, you can do so from the data inside the `HttpClientSuccess`:

```scala
case HttpClientSuccess(content) => content.headers
```

See [HeaderExample.scala](/blob/master/src/it/scala/io/moia/scalaHttpClient/HeaderExample.scala) for a complete example.

## Publishing

You need an account on https://oss.sonatype.org that can [access](https://issues.sonatype.org/browse/OSSRH-52948) the `io.moia` group.

Add your credentials to `~/.sbt/sonatype_credential` and run
```sbt
sbt:scala-http-client> +publishSigned
```

Then head to https://oss.sonatype.org/#stagingRepositories, select the repository, `Close` and then `Release` it.
