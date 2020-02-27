package io.moia.scalaHttpClient

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import io.moia.scalaHttpClient.ExampleModel.{DomainErrorObject, GatewayException, MySuccessObject}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object ExampleModel {
  case class MySuccessObject(foo: String)
  case class DomainErrorObject(errorMessage: String)
  case class GatewayException(msg: String) extends RuntimeException(msg)
}

object SimpleExample {

  implicit val system: ActorSystem                                = ActorSystem("test")
  implicit val executionContext: ExecutionContext                 = system.dispatcher
  implicit val mat: ActorMaterializer                             = ActorMaterializer()
  implicit val um1: Unmarshaller[HttpResponse, MySuccessObject]   = ???
  implicit val um2: Unmarshaller[HttpResponse, DomainErrorObject] = ???

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
}
