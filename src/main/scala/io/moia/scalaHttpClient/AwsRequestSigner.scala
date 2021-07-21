package io.moia.scalaHttpClient

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import com.typesafe.scalalogging.StrictLogging
import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.auth.signer.{Aws4Signer, AwsSignerExecutionAttribute}
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.http.{SdkHttpFullRequest, SdkHttpMethod}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest

import java.io.InputStream
import java.net.URI
import java.util
import scala.collection.compat._
import scala.collection.immutable._
import scala.concurrent.{blocking, Future}
import scala.jdk.CollectionConverters._

class AwsRequestSigner private (credentialsProvider: AwsCredentialsProvider, region: String)(implicit mat: Materializer) extends StrictLogging {
  private val awsSigner = Aws4Signer.create()
  private val executionAttributes = new ExecutionAttributes()
    .putAttribute(AwsSignerExecutionAttribute.SERVICE_SIGNING_NAME, "execute-api")
    .putAttribute(AwsSignerExecutionAttribute.SIGNING_REGION, Region.of(region))

  /** Signs the given HttpRequest
    *
    * @param request `HttpRequest` to be signed
    * @return `Future[HttpRequest]` the signed `HttpRequest`
    * @throws AlreadyAuthorizedException if the given `HttpRequest` already includes an "Authorize" header
    */
  def signRequest(request: HttpRequest): Future[HttpRequest] =
    if (isAlreadyAuthorized(request.headers))
      Future.failed(AlreadyAuthorizedException())
    else
      Future {
        val sdkRequest = toSdkRequest(request)

        val signedSdkRequest = blocking {
          // Ask the AWSCredentialsProvider for current credentials (e.g. from an existing key management system)
          val credentials = credentialsProvider.resolveCredentials()
          executionAttributes.putAttribute(AwsSignerExecutionAttribute.AWS_CREDENTIALS, credentials)
          awsSigner.sign(sdkRequest, executionAttributes)
        }
        // construct new HttpRequest with signed URI query params and headers
        HttpRequest(request.method, uri = Uri(signedSdkRequest.getUri.toString), getSdkHeaders(signedSdkRequest), request.entity)
      }(mat.executionContext)

  /** Checks if the given collection of `HttpHeader`s includes one "Authorization" header
    *
    * @param headers `Seq[HttpHeader]` headers of an Akka `HttpRequest`
    * @return true if "Authorization" header exists
    */
  private def isAlreadyAuthorized(headers: Seq[HttpHeader]): Boolean = headers.exists(_.is("authorization"))

  /** Constructs an `SdkHttpFullRequest` from Akka's `HttpRequest` for signing.
    *
    * @param request HttpRequest to convert
    * @return SdkHttpFullRequest
    */
  private def toSdkRequest(request: HttpRequest): SdkHttpFullRequest = {
    val content: InputStream = request.entity.dataBytes.runWith(StreamConverters.asInputStream())
    val headers: util.Map[String, util.List[String]] =
      request.headers.groupBy(_.name).view.mapValues(_.map(_.value)).map(h => h._1 -> h._2.toList.asJava).toMap.asJava
    val query: util.Map[String, util.List[String]] =
      request.uri.query().groupBy(_._1).view.mapValues(_.map(_._2)).map(q => q._1 -> q._2.toList.asJava).toMap.asJava
    SdkHttpFullRequest
      .builder()
      .uri(
        new URI(
          request.uri.scheme,
          request.uri.authority.toString,
          request.uri.path.toString,
          request.uri.rawQueryString.getOrElse(""),
          request.uri.fragment.getOrElse("")
        )
      )
      .rawQueryParameters(query)
      .method(SdkHttpMethod.fromValue(request.method.value))
      .headers(headers)
      .contentStreamProvider(() => content)
      .build()
  }

  /** Extracts the headers from the `SdkHttpFullRequest` as collection of Akka's `HttpHeader`s
    *
    * @param signedSdkRequest `SdkHttpFullRequest` after signing
    * @return `Seq[HttpHeader]` of the signedSdkRequest
    */
  private def getSdkHeaders(signedSdkRequest: SdkHttpFullRequest): scala.collection.immutable.Seq[HttpHeader] =
    signedSdkRequest
      .headers()
      .asScala
      .toSeq
      .map(h => HttpHeader.parse(h._1, h._2.asScala.head))
      .collect { case ParsingResult.Ok(header, _) =>
        header
      }
      .to(Seq)
}

object AwsRequestSigner extends StrictLogging {
  sealed trait AwsRequestSignerConfig

  object AwsRequestSignerConfig {
    final case class AssumeRole(roleArn: String, roleSessionName: String, awsRegion: String) extends AwsRequestSignerConfig

    final case class BasicCredentials(accessKey: String, secretKey: String, awsRegion: String) extends AwsRequestSignerConfig

    final case class Instance(awsRegion: String) extends AwsRequestSignerConfig
  }

  /** Construct an `AwsRequestSigner` from the given configuration.
    *
    * @param config `AwsRequestSignerConfig` to be used to construct one of the three config providers
    * @param mat    `Materializer` on which the `Future`s run
    * @return `AwsRequestSigner`
    */
  def fromConfig(config: AwsRequestSignerConfig)(implicit mat: Materializer): AwsRequestSigner =
    config match {
      case AwsRequestSignerConfig.Instance(awsRegion) =>
        val provider = InstanceProfileCredentialsProvider.create()
        new AwsRequestSigner(provider, awsRegion)
      case AwsRequestSignerConfig.AssumeRole(roleArn, roleSessionName, awsRegion) =>
        logger.info(s"Assuming role from config $config.")
        val provider: StsAssumeRoleCredentialsProvider = StsAssumeRoleCredentialsProvider
          .builder()
          .stsClient(StsClient.create())
          .refreshRequest(AssumeRoleRequest.builder().roleArn(roleArn).roleSessionName(roleSessionName).build())
          .build()
        new AwsRequestSigner(provider, awsRegion)
      case AwsRequestSignerConfig.BasicCredentials(accessKey, secretKey, awsRegion) =>
        logger.info("Using provided credentials for request signing")
        val provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        new AwsRequestSigner(provider, awsRegion)
    }
}

/** Being throw when the request to be signed already includes an "Authorization" header */
final case class AlreadyAuthorizedException(
    private val message: String = "The given request already includes an `Authorization` header. Won't sign again."
) extends Exception(message)
