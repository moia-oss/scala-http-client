package io.moia.scalaHttpClient

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.StrictLogging
import io.moia.scalaHttpClient.AwsRequestSigner.AwsRequestSignerConfig
import org.scalatest.{Inside, Inspectors}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try

class HttpClientTest extends TestSetup with Inside with StrictLogging {

  classOf[HttpClient].getSimpleName should {
    "sign requests" in {
      // given
      val awsRequestSigner: AwsRequestSigner =
        AwsRequestSigner.fromConfig(AwsRequestSignerConfig.BasicCredentials("example", "secret-key", "eu-central-1"))

      val capturedRequest = Promise[HttpRequest]()

      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, Some(awsRequestSigner)) {
        override def sendRequest(req: HttpRequest): Future[HttpResponse] = {
          capturedRequest.success(req)
          Future.successful(HttpResponse())
        }
      }

      // when
      testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue

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
        override def sendRequest(req: HttpRequest): Future[HttpResponse] = {
          capturedRequest.success(req)
          Future.successful(HttpResponse())
        }
      }

      // when
      testHttpClient
        .request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq(customerHeader), Deadline.now + 10.seconds)
        .futureValue

      // then
      capturedRequest.future.futureValue.getHeader(CustomHeader.name).get.value should ===("foobar")
    }

    "treat status 2xx as success" in {
      val statusCodes = List(StatusCodes.OK, StatusCodes.Accepted, StatusCodes.Created, StatusCodes.NoContent)

      Inspectors.forAll(statusCodes) { statusCode =>
        inside(dummyRequestWithFixResponseStatus(statusCode)) { case HttpClientSuccess(_) =>
          succeed
        }
      }
    }

    "return an HttpClientError on StatusCode 400 without an Entity" in {
      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
        override def sendRequest(req: HttpRequest): Future[HttpResponse] =
          Future.successful(HttpResponse().withStatus(400).withEntity(HttpEntity.Empty))
      }

      // When
      val result: Future[HttpClientResponse] =
        testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue shouldBe a[HttpClientError]
    }

    "return a DomainError on StatusCode 400 with an Entity" in {
      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
        override def sendRequest(req: HttpRequest): Future[HttpResponse] =
          Future.successful(HttpResponse().withStatus(400).withEntity(HttpEntity("Test")))
      }

      // When
      val result: Future[HttpClientResponse] =
        testHttpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue shouldBe a[DomainError]
      inside(result.futureValue) { case DomainError(content) =>
        Unmarshal(content.entity).to[String].futureValue shouldBe "Test"
      }
    }

    "retry on exceptions" in {
      val capturedRequest1 = Promise[HttpRequest]()
      val capturedRequest2 = Promise[HttpRequest]()
      val capturedRequest3 = Promise[HttpRequest]()

      val entity = HttpEntity("Example")

      val testHttpClient = new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
        override def sendRequest(req: HttpRequest): Future[HttpResponse] =
          if (!capturedRequest1.isCompleted) {
            capturedRequest1.success(req)
            throw new Exception("First Exception")
          } else if (!capturedRequest2.isCompleted) {
            capturedRequest2.success(req)
            throw new Exception("Second Exception")
          } else if (!capturedRequest3.isCompleted) {
            capturedRequest3.success(req)
            Future.successful(HttpResponse())
          } else
            fail("More than three requests")
      }

      // when
      testHttpClient.request(HttpMethods.POST, entity, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue

      // then
      capturedRequest1.future.futureValue.entity should ===(entity)
      capturedRequest2.future.futureValue.entity should ===(entity)
      capturedRequest3.future.futureValue.entity should ===(entity)
    }
  }

  private[this] def dummyRequestWithFixResponseStatus(status: StatusCode): HttpClientResponse =
    new HttpClient(httpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None) {
      override def sendRequest(req: HttpRequest): Future[HttpResponse] = Future.successful(HttpResponse(status))
    }.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds).futureValue
}
