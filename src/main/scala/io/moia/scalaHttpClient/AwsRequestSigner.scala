package io.moia.scalaHttpClient

import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import com.amazonaws.auth._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{blocking, Future}

class AwsRequestSigner private (credentialsProvider: AWSCredentialsProvider, region: String)(implicit mat: Materializer)
    extends StrictLogging {
  private val awsSigner = {
    val result = new AWS4Signer()

    result.setServiceName("execute-api")
    result.setRegionName(region)

    result
  }

  def signRequest(request: HttpRequest): Future[HttpRequest] = {
    val signableHttpRequest = new SignableHttpRequest(request)

    Future {
      blocking {
        awsSigner.sign(signableHttpRequest, credentialsProvider.getCredentials)
      }

      signableHttpRequest.getOriginalRequestObject
    }(mat.executionContext)
  }
}

object AwsRequestSigner extends StrictLogging {
  sealed trait AwsRequestSignerConfig

  object AwsRequestSignerConfig {
    final case class AssumeRole(roleArn: String, roleSessionName: String, awsRegion: String)   extends AwsRequestSignerConfig
    final case class BasicCredentials(accessKey: String, secretKey: String, awsRegion: String) extends AwsRequestSignerConfig
    final case class Instance(awsRegion: String)                                               extends AwsRequestSignerConfig
  }

  def fromConfig(config: AwsRequestSignerConfig)(implicit mat: Materializer): AwsRequestSigner =
    config match {
      case AwsRequestSignerConfig.Instance(awsRegion) =>
        val provider = InstanceProfileCredentialsProvider.getInstance()
        new AwsRequestSigner(provider, awsRegion)
      case AwsRequestSignerConfig.AssumeRole(roleArn, roleSessionName, awsRegion) =>
        logger.info(s"Assuming role from config $config.")
        val provider = new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, roleSessionName).build()
        new AwsRequestSigner(provider, awsRegion)
      case AwsRequestSignerConfig.BasicCredentials(accessKey, secretKey, awsRegion) =>
        logger.info("Using provided credentials for request signing")
        val provider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey))
        new AwsRequestSigner(provider, awsRegion)
    }
}
