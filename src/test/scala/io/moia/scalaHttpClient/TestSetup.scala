package io.moia.scalaHttpClient

import java.time.Clock
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{HttpMethod, HttpResponse, Uri}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

trait TestSetup extends AnyWordSpecLike with Matchers with FutureValues {
  implicit val system: ActorSystem[_]             = ActorSystem(Behaviors.empty, "test")
  implicit val executionContext: ExecutionContext = system.executionContext

  val clock: Clock = Clock.systemUTC()
  val httpMetrics: HttpMetrics[NoLoggingContext] = new HttpMetrics[NoLoggingContext] {
    override def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse)(implicit ctx: NoLoggingContext): Unit = ()
  }

  val httpClientConfig: HttpClientConfig = HttpClientConfig("http", "127.0.0.1", 8888)

  val retryConfig: RetryConfig = RetryConfig.default
}
