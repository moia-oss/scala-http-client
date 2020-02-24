package io.moia.scalaHttpClient

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Retry-After`, RetryAfterDateTime, RetryAfterDuration}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class HttpClient(
    config: HttpClientConfig,
    gatewayType: String,
    httpMetrics: HttpMetrics[String],
    retryConfig: RetryConfig,
    clock: Clock,
    awsRequestSigner: Option[AwsRequestSigner]
)(
    implicit system: ActorSystem
) extends LoggingHttpClient[String](
      config,
      gatewayType,
      httpMetrics,
      retryConfig,
      clock,
      Logger.takingImplicit(LoggerFactory.getLogger(getClass.getName))((msg: String, _: String) => msg),
      awsRequestSigner
    ) {
  override def request(
      method: HttpMethod,
      entity: MessageEntity,
      path: String,
      headers: immutable.Seq[HttpHeader],
      deadline: Deadline,
      queryString: Option[String]
  )(implicit executionContext: ExecutionContext, ctx: String = ""): Future[HttpClientResponse] =
    super.request(method, entity, path, headers, deadline, queryString)
}

class LoggingHttpClient[LoggingContext](
    config: HttpClientConfig,
    gatewayType: String,
    httpMetrics: HttpMetrics[LoggingContext],
    retryConfig: RetryConfig,
    clock: Clock,
    logger: LoggerTakingImplicit[LoggingContext],
    awsRequestSigner: Option[AwsRequestSigner]
)(implicit system: ActorSystem)
    extends HttpLayer(config, gatewayType, httpMetrics, retryConfig, clock, logger, awsRequestSigner) {
  override protected def sendRequest: HttpRequest => Future[HttpResponse] = Http().singleRequest(_)
}

abstract class HttpLayer[LoggingContext](
    config: HttpClientConfig,
    gatewayType: String,
    httpMetrics: HttpMetrics[LoggingContext],
    retryConfig: RetryConfig,
    clock: Clock,
    logger: LoggerTakingImplicit[LoggingContext],
    awsRequestSigner: Option[AwsRequestSigner] = None
)(
    implicit system: ActorSystem
) {

  protected def sendRequest: HttpRequest => Future[HttpResponse]

  def request(
      method: HttpMethod,
      entity: RequestEntity,
      path: String,
      headers: immutable.Seq[HttpHeader],
      deadline: Deadline,
      queryString: Option[String] = None
  )(
      implicit executionContext: ExecutionContext,
      ctx: LoggingContext
  ): Future[HttpClientResponse] =
    if (deadline.isOverdue()) {
      Future.successful(DeadlineExpired())
    } else {
      buildRequest(method, entity, path, headers, queryString).flatMap(executeRequest(_, 1, deadline))
    }

  private[this] def buildRequest(
      method: HttpMethod,
      entity: RequestEntity,
      path: String,
      headers: immutable.Seq[HttpHeader],
      queryString: Option[String]
  ): Future[HttpRequest] = {
    val uri     = Uri.from(config.scheme, "", config.host, config.port, path, queryString)
    val request = HttpRequest(method, uri, headers, entity)

    awsRequestSigner match {
      case Some(signer) => signer.signRequest(request)
      case None         => Future.successful(request)
    }
  }

  private[this] def executeRequest(request: HttpRequest, tryNumber: Int, deadline: Deadline)(
      implicit ec: ExecutionContext,
      ctx: LoggingContext
  ): Future[HttpClientResponse] =
    Future
      .successful(request)
      .andThen(logRequest)
      .flatMap(sendRequest)
      .andThen(logResponse(request))
      .andThen(logRetryAfter)
      .flatMap(strictify)
      .flatMap(handleResponse(tryNumber, deadline, request))
      .recoverWith(handleErrors(tryNumber, deadline, request))

  private[this] def handleResponse(tryNumber: Int, deadline: Deadline, httpRequest: HttpRequest)(
      response: HttpResponse
  )(implicit ec: ExecutionContext, ctx: LoggingContext): Future[HttpClientResponse] = response match {
    case response @ HttpResponse(StatusCodes.Success(_), _, _, _)                => Future.successful(HttpClientSuccess(response))
    case response @ HttpResponse(StatusCodes.BadRequest, _, HttpEntity.Empty, _) => Future.successful(HttpClientError(response))
    case response @ HttpResponse(StatusCodes.BadRequest, _, _, _)                => Future.successful(DomainError(response))
    case response @ HttpResponse(code, _, _, _) if shouldBeRetried(code)         => retryWithConfig(tryNumber, httpRequest, response, deadline)
    case other                                                                   => Future.successful(HttpClientError(other))
  }

  private def strictify(response: HttpResponse)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    response.toStrict(retryConfig.strictifyResponseTimeout)
  }

  private[this] def shouldBeRetried(statusCode: StatusCode): Boolean =
    Seq(StatusCodes.RequestTimeout, StatusCodes.TooManyRequests, StatusCodes.ServiceUnavailable, StatusCodes.InternalServerError)
      .contains(statusCode)

  private[this] def retryCount(statusCode: StatusCode): Int = statusCode match {
    case StatusCodes.RequestTimeout      => retryConfig.retriesRequestTimeout
    case StatusCodes.TooManyRequests     => retryConfig.retriesTooManyRequests
    case StatusCodes.ServiceUnavailable  => retryConfig.retriesServiceUnavailable
    case StatusCodes.InternalServerError => retryConfig.retriesServerError
    case _                               => retryConfig.retriesServerError
  }

  private[this] def retryWithConfig(tryNum: Int, request: HttpRequest, response: HttpResponse, deadline: Deadline)(
      implicit ec: ExecutionContext,
      ctx: LoggingContext
  ): Future[HttpClientResponse] =
    if (deadline.isOverdue()) {
      logger.info(s"[$gatewayType] Try #$tryNum: Deadline has expired.")
      Future.successful(DeadlineExpired(Some(response)))
    } else if (tryNum <= retryCount(response.status)) {
      val delay: FiniteDuration = calculateDelay(response.header[`Retry-After`], tryNum)

      if ((deadline + delay).isOverdue()) {
        logger.info(s"[$gatewayType] Try #$tryNum: Retry in ${delay.toMillis}ms would exceed Deadline. Giving up.")
        Future.successful(DeadlineExpired(Some(response)))
      } else {
        logger.info(s"[$gatewayType] Try #$tryNum: Retrying in ${delay.toMillis}ms.")
        akka.pattern.after(delay, system.scheduler)(executeRequest(request, tryNum + 1, deadline))
      }
    } else {
      logger.info(s"[$gatewayType] Try #$tryNum: No retries left. Giving up.")
      Future.successful(HttpClientError(response))
    }

  private[this] def calculateDelay(retryAfter: Option[`Retry-After`], tryNum: Int): FiniteDuration = retryAfter match {
    case Some(value) =>
      value.delaySecondsOrDateTime match {
        case RetryAfterDuration(delayInSeconds) => delayInSeconds.seconds
        case RetryAfterDateTime(dateTime)       => (dateTime.clicks - clock.instant().toEpochMilli).millis
      }
    case None =>
      val factor = scala.math.pow(2.0, tryNum.toDouble - 1.0).toLong
      retryConfig.initialBackoff * factor
  }

  private[this] def handleErrors(tryNum: Int, deadline: Deadline, request: HttpRequest)(
      implicit ec: ExecutionContext,
      ctx: LoggingContext
  ): PartialFunction[Throwable, Future[HttpClientResponse]] = {
    case NonFatal(e) if tryNum <= retryConfig.retriesException =>
      val delay: FiniteDuration = calculateDelay(None, tryNum)
      logger.info(s"[$gatewayType] Exception in request: ${e.getMessage}, retrying in ${delay.toMillis}ms.", e)
      akka.pattern.after(delay, system.scheduler)(executeRequest(request, tryNum + 1, deadline))
    case NonFatal(e) =>
      logger.warn(s"[$gatewayType] Exception in request: ${e.getMessage}, retries exhausted, giving up.", e)
      Future.successful(DeadlineExpired(None))
  }

  private[this] def logRequest[T](implicit ctx: LoggingContext): PartialFunction[Try[HttpRequest], Unit] = {
    case Success(request) => logger.debug(s"[$gatewayType] Sending request to ${request.method.value} ${request.uri}.")
  }

  private[this] def logRetryAfter(implicit ctx: LoggingContext): PartialFunction[Try[HttpResponse], Unit] = {
    case Success(response) if response.header[`Retry-After`].isDefined =>
      logger.info(s"[$gatewayType] Received retry-after header with value ${response.header[`Retry-After`]}")
  }

  private[this] def logResponse(request: HttpRequest)(implicit ctx: LoggingContext): PartialFunction[Try[HttpResponse], Unit] = {
    case Success(response) =>
      httpMetrics.meterResponse(request.method, request.uri.path, response)
      logger.debug(s"[$gatewayType] Received response ${response.status} from ${request.method.value} ${request.uri}.")
    case Failure(e) =>
      logger.info(s"[$gatewayType] Exception in Gateway for ${request.method.value} ${request.uri}: ${e.getMessage}", e)
  }
}

sealed abstract class HttpClientResponse
final case class HttpClientSuccess(content: HttpResponse) extends HttpClientResponse
final case class DomainError(content: HttpResponse)       extends HttpClientResponse

sealed abstract class HttpClientFailure                                extends HttpClientResponse
final case class HttpClientError(content: HttpResponse)                extends HttpClientFailure
final case class DeadlineExpired(content: Option[HttpResponse] = None) extends HttpClientFailure
final case class ExceptionOccurred(exception: Exception)               extends HttpClientFailure
