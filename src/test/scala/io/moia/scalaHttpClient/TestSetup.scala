package io.moia.scalaHttpClient

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethod, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait TestSetup extends AnyWordSpecLike with Matchers with FutureValues {
  implicit val system: ActorSystem                = ActorSystem("test")
  implicit val mat: ActorMaterializer             = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val clock: Clock = Clock.systemUTC()
  val httpMetrics: HttpMetrics[NoLoggingContext] = new HttpMetrics[NoLoggingContext] {
    override def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse)(implicit ctx: NoLoggingContext): Unit = ()
  }

  val httpClientConfig: HttpClientConfig = HttpClientConfig("http", "127.0.0.1", 8888)

  val retryConfig: RetryConfig =
    RetryConfig(
      retriesTooManyRequests    = 2,
      retriesServiceUnavailable = 0,
      retriesRequestTimeout     = 0,
      retriesServerError        = 0,
      retriesException          = 3,
      initialBackoff            = 10.millis,
      strictifyResponseTimeout  = 1.second
    )
}
