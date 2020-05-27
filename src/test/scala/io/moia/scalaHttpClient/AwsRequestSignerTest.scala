package io.moia.scalaHttpClient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, Uri}
import io.moia.scalaHttpClient.AwsRequestSigner.AwsRequestSignerConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{FiniteDuration, _}

class AwsRequestSignerTest extends AnyWordSpecLike with Matchers with FutureValues {
  private implicit val system: ActorSystem              = ActorSystem("test")
  protected implicit val patienceConfig: FiniteDuration = 2.seconds

  classOf[AwsRequestSigner].getSimpleName should {
    "add an Authorization header to a vanilla HttpRequest" in {
      val unsignedRequest: HttpRequest = HttpRequest(uri = Uri(s"https://www.moia.io"))

      val underTest = AwsRequestSigner.fromConfig(AwsRequestSignerConfig.BasicCredentials("AKIDEXAMPLE", "secret-key", "eu-central-1"))

      val result = underTest.signRequest(unsignedRequest).futureValue

      result.headers.find(_.name() == "Authorization").map(_.value()) should not be empty
      result.uri.rawQueryString shouldBe unsignedRequest.uri.rawQueryString
    }

    "add the correct headers to a GET request with a query-string" in {
      // Name in AWS test data: get-vanilla-query-order
      val unsignedRequest: HttpRequest = HttpRequest(uri = Uri(s"https://www.moia.io/?Param1=value1&Param2=value2"))

      val underTest: AwsRequestSigner =
        AwsRequestSigner.fromConfig(AwsRequestSignerConfig.BasicCredentials("AKIDEXAMPLE", "secret-key", "eu-central-1"))

      val result: HttpRequest = underTest.signRequest(unsignedRequest).futureValue
      val regex =
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/[0-9]{8}/eu-central-1/execute-api/aws4_request, SignedHeaders=host;x-amz-date, Signature=.*"

      result.headers.find(_.name() == "Authorization").map(_.value()) should not be empty
      result.headers
        .find(_.name() == "Authorization")
        .map(_.value())
        .getOrElse("")
        .matches(regex) shouldBe true

      val paramValuePairs = unsignedRequest.uri.rawQueryString.getOrElse("").split("&")
      for (i <- paramValuePairs)
        result.uri.rawQueryString.getOrElse("").contains(i) shouldBe true
    }
  }
}
