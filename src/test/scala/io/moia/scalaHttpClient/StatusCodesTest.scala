package io.moia.scalaHttpClient

import akka.http.scaladsl.model._
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.verify.VerificationTimes

import scala.collection.immutable
import scala.concurrent.duration._

class StatusCodesTest extends TestSetup with MockServer {

  override implicit val defaultAwaitDuration: FiniteDuration = 1.second

  "respect retry configuration" when {
    s"the server responds with ${StatusCodes.TooManyRequests}" in {
      // Given
      val retryConfig = RetryConfig.default.copy(retriesTooManyRequests = 2)
      val httpClient  = new HttpClient(mockServerHttpClientConfig, "TestGateway", httpMetrics, retryConfig, clock, None)

      // status 429
      getClientAndServer
        .when(request().withPath("/test").withMethod("POST"), Times.unlimited())
        .respond(response().withStatusCode(StatusCodes.TooManyRequests.intValue))

      // When
      val result = httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue should matchPattern { case HttpClientError(_) => }
      getClientAndServer.verify(request().withPath("/test").withMethod("POST"), VerificationTimes.exactly(3))
      succeed
    }

    s"the server responds with ${StatusCodes.ServiceUnavailable}" in {
      val retryConfig = RetryConfig.default.copy(retriesServiceUnavailable = 2)
      val httpClient  = new HttpClient(mockServerHttpClientConfig, "TestClient", httpMetrics, retryConfig, clock, None)

      getClientAndServer
        .when(request().withPath("/test").withMethod("POST"), Times.unlimited())
        .respond(response().withStatusCode(StatusCodes.ServiceUnavailable.intValue))

      // When
      val result = httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue should matchPattern { case HttpClientError(_) => }
      getClientAndServer.verify(
        request().withPath("/test").withMethod("POST").withSecure(false).withKeepAlive(true),
        VerificationTimes.exactly(3)
      )
      succeed
    }

    s"the server responds with ${StatusCodes.RequestTimeout}" in {
      val retryConfig = RetryConfig.default.copy(retriesRequestTimeout = 2)
      val httpClient  = new HttpClient(mockServerHttpClientConfig, "TestClient", httpMetrics, retryConfig, clock, None)

      getClientAndServer
        .when(request().withPath("/test").withMethod("POST"), Times.unlimited())
        .respond(response().withStatusCode(StatusCodes.RequestTimeout.intValue))

      // When
      val result = httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue should matchPattern { case HttpClientError(_) => }
      getClientAndServer.verify(request().withPath("/test").withMethod("POST"), VerificationTimes.exactly(3))
      succeed
    }

    s"the server responds with ${StatusCodes.InternalServerError}" in {
      val retryConfig = RetryConfig.default.copy(retriesInternalServerError = 2)
      val httpClient  = new HttpClient(mockServerHttpClientConfig, "TestClient", httpMetrics, retryConfig, clock, None)

      getClientAndServer
        .when(request().withPath("/test").withMethod("POST"), Times.unlimited())
        .respond(response().withStatusCode(500))

      // When
      val result = httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue should matchPattern { case HttpClientError(_) => }
      getClientAndServer.verify(request().withPath("/test").withMethod("POST"), VerificationTimes.exactly(3))
      succeed
    }

    s"the server responds with ${StatusCodes.ImATeapot}" in {
      val retryConfig = RetryConfig.default.copy(retriesClientError = 2)
      val httpClient  = new HttpClient(mockServerHttpClientConfig, "TestClient", httpMetrics, retryConfig, clock, None)

      getClientAndServer
        .when(request().withPath("/test").withMethod("POST"), Times.unlimited())
        .respond(response().withStatusCode(418))

      // When
      val result = httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, Deadline.now + 10.seconds)

      // Then
      result.futureValue should matchPattern { case HttpClientError(_) => }
      getClientAndServer.verify(request().withPath("/test").withMethod("POST"), VerificationTimes.exactly(3))
      succeed
    }
  }

  "not retry" when {
    "deadline would be exceeded" in {
      val httpClient = new HttpClient(mockServerHttpClientConfig, "TestClient", httpMetrics, retryConfig, clock, None)

      getClientAndServer
        .when(request().withPath("/test").withMethod("POST"), Times.unlimited())
        .respond(response().withStatusCode(StatusCodes.TooManyRequests.intValue))

      val veryShortDeadline = Deadline.now + 1.milli
      // When
      val result = httpClient.request(HttpMethods.POST, HttpEntity.Empty, "/test", immutable.Seq.empty, veryShortDeadline)

      // Then
      result.futureValue should matchPattern { case DeadlineExpired(_) => }
      getClientAndServer.verify(request().withPath("/test").withMethod("POST"), VerificationTimes.exactly(1))
      succeed
    }
  }
}
