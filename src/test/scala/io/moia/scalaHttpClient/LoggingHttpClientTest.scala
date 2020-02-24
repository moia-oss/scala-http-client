package io.moia.scalaHttpClient

import akka.http.scaladsl.model._
import com.typesafe.scalalogging.{CanLog, Logger, LoggerTakingImplicit}
import org.slf4j.{LoggerFactory, MDC}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

class LoggingHttpClientTest extends TestSetup {

  val typedHttpMetrics: HttpMetrics[LoggingContext] = new HttpMetrics[LoggingContext] {
    override def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse)(implicit ctx: LoggingContext): Unit = ()
  }

  case class LoggingContext(context: String)

  private implicit val canLogString: CanLog[LoggingContext] = new CanLog[LoggingContext] {
    val prevContext: String = MDC.get("context")

    override def logMessage(originalMsg: String, ctx: LoggingContext): String = {
      // you can put things to the MDC here
      MDC.put("context", ctx.context)

      // …or influence the string that is going to be logged:
      s"$originalMsg for (${ctx.context})"
    }
    override def afterLog(ctx: LoggingContext): Unit =
      if (prevContext != null) {
        MDC.put("context", prevContext)
      } else MDC.remove("context")
  }

  private implicit val theLogger: LoggerTakingImplicit[LoggingContext] = Logger.takingImplicit(LoggerFactory.getLogger(getClass.getName))
  private implicit val ctx: LoggingContext                             = LoggingContext("Logging Context")

  classOf[LoggingHttpClient[LoggingContext]].getSimpleName should {
    "take a customer logger" in {
      // given
      val testHttpClient =
        new LoggingHttpClient[LoggingContext](httpClientConfig, "TestGateway", typedHttpMetrics, retryConfig, clock, theLogger, None) {
          override def sendRequest: HttpRequest => Future[HttpResponse] = (_: HttpRequest) => Future.successful(HttpResponse())
        }

      // when
      val _ =
        testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue

      // then succeed if it compiles
      succeed
    }
  }
}
