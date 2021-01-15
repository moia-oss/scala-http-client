package io.moia.scalaHttpClient

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import io.moia.scalaHttpClient.ExampleModel.{DomainErrorObject, GatewayException, MySuccessObject}

import java.time.Clock
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object ExampleModel {
  final case class MySuccessObject(foo: String)
  final case class DomainErrorObject(errorMessage: String)
  final case class GatewayException(msg: String) extends RuntimeException(msg)
}

object SimpleExample {
  implicit val system: ActorSystem[_]                             = ActorSystem(Behaviors.empty, "test")
  implicit val executionContext: ExecutionContext                 = system.executionContext
  implicit val um1: Unmarshaller[HttpResponse, MySuccessObject]   = ???
  implicit val um2: Unmarshaller[HttpResponse, DomainErrorObject] = ???

  // create the client
  val httpClient = new HttpClient(
    config           = HttpClientConfig("http", "127.0.0.1", 8888),
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
    headers  = Seq.empty,
    deadline = Deadline.now + 10.seconds
  )

  // map the response to your model
  response.flatMap {
    case HttpClientSuccess(content) => Unmarshal(content).to[MySuccessObject].map(Right(_))
    case DomainError(content)       => Unmarshal(content).to[DomainErrorObject].map(Left(_))
    case failure: HttpClientFailure => throw GatewayException(failure.toString)
  }
}
