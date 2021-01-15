package io.moia.scalaHttpClient

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model._
import com.typesafe.scalalogging._
import io.moia.scalaHttpClient.CustomLogging.LoggingContext
import org.slf4j.LoggerFactory

import java.time.Clock
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object CustomLogging {
  final case class LoggingContext(context: String)

  implicit val canLogString: CanLog[LoggingContext] = new CanLog[LoggingContext] {
    override def logMessage(originalMsg: String, ctx: LoggingContext): String = ???
    override def afterLog(ctx: LoggingContext): Unit                          = ???
  }

  val theLogger: LoggerTakingImplicit[LoggingContext] = Logger.takingImplicit(LoggerFactory.getLogger(getClass.getName))
}

object Example {
  implicit val system: ActorSystem[_]             = ActorSystem(Behaviors.empty, "test")
  implicit val executionContext: ExecutionContext = system.executionContext

  // create the client
  val httpClient = new LoggingHttpClient[LoggingContext](
    config           = HttpClientConfig("http", "127.0.0.1", 8888),
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
  httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", Seq.empty, Deadline.now + 10.seconds)
}
