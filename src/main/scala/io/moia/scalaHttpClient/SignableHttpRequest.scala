package io.moia.scalaHttpClient

import java.io.{BufferedInputStream, InputStream}
import java.net.URI
import java.util
import java.util.concurrent.atomic.AtomicReference

import SignableHttpRequest.UnparsableHeaderException
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpEntity, HttpHeader, HttpRequest, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import com.amazonaws.http.HttpMethodName
import com.amazonaws.{ReadLimitInfo, SignableRequest}

import scala.collection.compat._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.jdk.CollectionConverters._

/**
  * Wrapper to adapt the immutable HttpRequest to the mutable SignableRequest interface from AWS
  *
  * @param request the request to be signed
  * @param mat the actor materializer
  */
final class SignableHttpRequest(request: HttpRequest)(implicit mat: Materializer) extends SignableRequest[HttpRequest] {
  private val inputStreamReadTimeout: FiniteDuration = 10.seconds

  private val currentRequest: AtomicReference[HttpRequest] = new AtomicReference(request)

  override def addHeader(name: String, value: String): Unit = HttpHeader.parse(name, value) match {
    case ParsingResult.Ok(header, _)    => updateRequest(_.addHeader(header))
    case ParsingResult.Error(errorInfo) => throw UnparsableHeaderException(s"Header parse error: ${errorInfo.formatPretty}")
  }

  override def addParameter(name: String, value: String): Unit =
    updateRequest { request =>
      val uri   = request.uri
      val query = uri.query()
      request.withUri(uri.withQuery((name -> value) +: query))
    }

  override def setContent(content: InputStream): Unit = updateRequest {
    _.mapEntity(entity => HttpEntity(entity.contentType, StreamConverters.fromInputStream(() => content)))
  }

  override def getHeaders: util.Map[String, String] = fromRequest(_.headers.map(h => h.name() -> h.value()).toMap.asJava)

  override def getResourcePath: String = fromRequest(_.uri.path.toString())

  override def getParameters: util.Map[String, util.List[String]] =
    fromRequest(_.uri.query().toMultiMap.view.mapValues(_.asJava).toMap.asJava)

  override def getEndpoint: URI = fromRequest(r => toEndpoint(r.uri))

  override def getHttpMethod: HttpMethodName = HttpMethodName.fromValue(fromRequest(_.method.value))

  override def getTimeOffset: Int = 0

  override def getContent: InputStream =
    new BufferedInputStream(
      fromRequest(
        _.entity
          .getDataBytes()
          .runWith(
            StreamConverters.asInputStream(inputStreamReadTimeout),
            mat
          )
      )
    )

  override def getContentUnwrapped: InputStream = getContent

  override def getReadLimitInfo: ReadLimitInfo = () => -1

  override def getOriginalRequestObject: HttpRequest = currentRequest.get()

  @SuppressWarnings(Array("NullParameter")) // Scapegoat complains when we pass an explicit null as a parameter
  private[this] def toEndpoint(uri: Uri): URI = new URI(uri.scheme, uri.authority.copy(userinfo = "").toString(), null, null, null)

  private[this] def updateRequest(f: HttpRequest => HttpRequest): Unit = {
    currentRequest.getAndUpdate(request => f(request))
    ()
  }

  private[this] def fromRequest[R](f: HttpRequest => R): R = f(currentRequest.get())
}

object SignableHttpRequest {
  final case class UnparsableHeaderException(msg: String) extends RuntimeException(msg)
}
