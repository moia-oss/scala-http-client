package io.moia.scalaHttpClient

import java.time.Clock

import AwsRequestSigner.AwsRequestSignerConfig
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Inside, Inspectors}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class HttpClientTest extends AnyWordSpecLike with Matchers with FutureValues with Inside {

  private implicit val system: ActorSystem                = ActorSystem("test")
  private implicit val executionContext: ExecutionContext = system.dispatcher
  private val httpClientConfig: HttpClientConfig          = HttpClientConfig("http", isSecureConnection = false, "127.0.0.1", 8888)
  private val clock: Clock                                = Clock.systemUTC()
  private val httpMetrics: HttpMetrics                    = (_: HttpMethod, _: Uri.Path, _: HttpResponse) => ()
  private val retryConfig: RetryConfig =
    RetryConfig(
      retriesTooManyRequests = 2,
      retriesServiceUnavailable = 0,
      retriesRequestTimeout = 0,
      retriesServerError = 0,
      retriesException = 3,
      initialBackoff = 10.millis,
      strictifyResponseTimeout = 1.second
    )

  classOf[HttpClient].getSimpleName should {
    "sign requests" in {
      // given
      val awsRequestSigner: AwsRequestSigner =
        AwsRequestSigner.fromConfig(AwsRequestSignerConfig.BasicCredentials("example", "secret-key", "eu-central-1"))

      val capturedRequest = Promise[HttpRequest]()

      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, Some(awsRequestSigner)) {
        override def sendRequest: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => {
          capturedRequest.success(req)
          Future.successful(HttpResponse())
        }
      }

      // when
      val _ = testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue

      // then
      (capturedRequest.future.futureValue.headers
        .map(_.name.toLowerCase) should contain).allElementsOf(List("host", "x-amz-date", "authorization"))
    }

    "add custom headers" in {
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

      val customerHeader = new CustomHeader("foobar")

      val capturedRequest = Promise[HttpRequest]()

      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
        override def sendRequest: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => {
          capturedRequest.success(req)
          Future.successful(HttpResponse())
        }
      }

      // when
      val _ =
        testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq(customerHeader), Deadline.now + 10.seconds).futureValue

      // then
      capturedRequest.future.futureValue.getHeader(CustomHeader.name).get.value should ===("foobar")
    }

    "treat status 2xx as success" in {
      val statusCodes = List(StatusCodes.OK, StatusCodes.Accepted, StatusCodes.Created, StatusCodes.NoContent)

      Inspectors.forAll(statusCodes) { statusCode =>
        inside(dummyRequestWithFixResponseStatus(statusCode)) {
          case HttpClientSuccess(_) => succeed
        }
      }
    }

    "retry on exceptions" in {
      val capturedRequest1 = Promise[HttpRequest]()
      val capturedRequest2 = Promise[HttpRequest]()
      val capturedRequest3 = Promise[HttpRequest]()

      val entity = HttpEntity.apply("Example")

      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
        override def sendRequest: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => {
          if (!capturedRequest1.isCompleted) {
            capturedRequest1.success(req)
            throw new Exception("First Exception")
          } else if (!capturedRequest2.isCompleted) {
            capturedRequest2.success(req)
            throw new Exception("Second Exception")
          } else if (!capturedRequest3.isCompleted) {
            capturedRequest3.success(req)
            Future.successful(HttpResponse())
          } else {
            fail("More than three requests")
          }
        }
      }

      // when
      val _ = testHttpClient.request(HttpMethods.POST, entity, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue

      // then
      capturedRequest1.future.futureValue.entity should ===(entity)
      capturedRequest2.future.futureValue.entity should ===(entity)
      capturedRequest3.future.futureValue.entity should ===(entity)

    }
  }

  private[this] def dummyRequestWithFixResponseStatus(status: StatusCode): HttpClientResponse =
    new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
      override def sendRequest: HttpRequest => Future[HttpResponse] = _ => Future.successful(HttpResponse(status))
    }.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue
}
