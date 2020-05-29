package io.moia.scalaHttpClient

import java.io.InputStream
import java.net.URI
import java.util

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

import scala.collection.compat._
import scala.collection.immutable._
import scala.concurrent.{blocking, Future}
import scala.jdk.CollectionConverters._

class AwsRequestSigner private (credentialsProvider: AwsCredentialsProvider, region: String)(implicit mat: Materializer)
    extends StrictLogging {
  private val awsSigner   = Aws4Signer.create()
  private val credentials = credentialsProvider.resolveCredentials()
  private val executionAttributes = new ExecutionAttributes()
    .putAttribute(AwsSignerExecutionAttribute.AWS_CREDENTIALS, credentials)
    .putAttribute(AwsSignerExecutionAttribute.SERVICE_SIGNING_NAME, "execute-api")
    .putAttribute(AwsSignerExecutionAttribute.SIGNING_REGION, Region.of(region))

  def signRequest(request: HttpRequest): Future[HttpRequest] =
    Future {
      val sdkRequest = toSdkRequest(request)
      val signedSdkRequest = blocking {
        awsSigner.sign(sdkRequest, executionAttributes)
      }
      // construct new HttpRequest with signed URI query params and headers
      HttpRequest(request.method, uri = Uri(signedSdkRequest.getUri.toString), getSdkHeaders(signedSdkRequest), request.entity)
    }(mat.executionContext)

  /**
    * Constructs an `SdkHttpFullRequest` from Akka's `HttpRequest` for signing.
    *
    * @param request HttpRequest to convert
    * @return SdkHttpFullRequest
    */
  private def toSdkRequest(request: HttpRequest): SdkHttpFullRequest = {
    val content: InputStream = request.entity.dataBytes.runWith(
      StreamConverters.asInputStream()
    )
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

  private def getSdkHeaders(signedSdkRequest: SdkHttpFullRequest): scala.collection.immutable.Seq[HttpHeader] =
    signedSdkRequest
      .headers()
      .asScala
      .toSeq
      .map(h => HttpHeader.parse(h._1, h._2.asScala.head))
      .collect {
        case ParsingResult.Ok(header, _) => header
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
          .refreshRequest(
            AssumeRoleRequest.builder().roleArn(roleArn).roleSessionName(roleSessionName).build()
          )
          .build()
        new AwsRequestSigner(provider, awsRegion)
      case AwsRequestSignerConfig.BasicCredentials(accessKey, secretKey, awsRegion) =>
        logger.info("Using provided credentials for request signing")
        val provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        new AwsRequestSigner(provider, awsRegion)
    }
}
