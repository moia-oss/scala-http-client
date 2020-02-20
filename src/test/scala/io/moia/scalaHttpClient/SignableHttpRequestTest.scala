package io.moia.scalaHttpClient

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import com.amazonaws.auth.{AWS4Signer, BasicAWSCredentials}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

class SignableHttpRequestTest extends AnyWordSpecLike with Matchers with FutureValues {
  private implicit def system: ActorSystem                = ActorSystem("test")
  private implicit val mat: ActorMaterializer             = ActorMaterializer()
  private implicit def executionContext: ExecutionContext = system.dispatcher
  private val amzDateFormat                               = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")

  // Test case data comes from AWS's own test spec:
  // https://docs.aws.amazon.com/de_de/general/latest/gr/signature-v4-test-suite.html

  classOf[SignableHttpRequest].getSimpleName should {
    "sign a vanilla GET request" in new TestEnv {
      // Name in AWS test data: get-vanilla
      expectAuthorizationToken(
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31"
      )
    }

    "sign a GET request with a query" in new TestEnv {
      // Name in AWS test data: get-vanilla-query-order
      override def query = "?Param1=value2&Param1=Value1"
      expectAuthorizationToken(
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=eedbc4e291e521cf13422ffca22be7d2eb8146eecf653089df300a15b2382bd1"
      )
    }
    // more tests should be added from the AWS test suite when we use more kinds of requests
  }

  private class TestEnv {
    def dateHeaderValue: String = "20150830T123600Z"
    def host: String            = "example.amazonaws.com"
    def path: String            = "/"
    def query: String           = ""

    def uri: Uri = Uri(s"https://$host$path$query")

    def dateHeader: HttpHeader = HttpHeader.parse("X-Amz-Date", dateHeaderValue) match {
      case Ok(header, _) => header
      case unexpected    => sys.error(s"Unexpected HTTP header parse result: $unexpected")
    }
    def hostHeader: HttpHeader    = Host(host)
    def headers: List[HttpHeader] = List(dateHeader, hostHeader)

    val unsignedRequest: HttpRequest = HttpRequest(headers = headers, uri = uri)
    val credentials                  = new BasicAWSCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
    val adapter                      = new SignableHttpRequest(unsignedRequest)
    val overrideDate: ZonedDateTime  = ZonedDateTime.from(amzDateFormat.parse(dateHeaderValue))

    val signer = new AWS4Signer
    signer.setServiceName("service")
    signer.setOverrideDate(Date.from(overrideDate.toInstant))
    signer.sign(adapter, credentials)

    val signedRequest: HttpRequest = adapter.getOriginalRequestObject

    def expectAuthorizationToken(expected: String): Unit = {
      signedRequest.headers.find(_.name() == "Authorization").map(_.value()) shouldBe Some(expected)
      ()
    }
  }
}
