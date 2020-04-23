package io.moia.scalaHttpClient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, Uri}
import io.moia.scalaHttpClient.AwsRequestSigner.AwsRequestSignerConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{FiniteDuration, _}

class AwsRequestSignerTest extends AnyWordSpecLike with Matchers with FutureValues {
  private implicit val system: ActorSystem              = ActorSystem("test")
  protected implicit val patienceConfig: FiniteDuration = 2.seconds

  classOf[AwsRequestSigner].getSimpleName should {
    "sign http requests" in {
      def dateHeader: HttpHeader = HttpHeader.parse("X-Amz-Date", "20150830T123600Z") match {
        case Ok(header, _) => header
        case unexpected    => sys.error(s"Unexpected HTTP header parse result: $unexpected")
      }

      val unsignedRequest: HttpRequest = HttpRequest(headers = List(dateHeader), uri = Uri(s"https://www.moia.io"))

      val underTest = AwsRequestSigner.fromConfig(AwsRequestSignerConfig.BasicCredentials("example", "secret-key", "eu-central-1"))

      val result = underTest.signRequest(unsignedRequest).futureValue

      result.headers.find(_.name() == "Authorization").map(_.value()) should not be empty
    }
  }
}
