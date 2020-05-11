package io.moia.scalaHttpClient

import akka.http.scaladsl.model.{HttpMethod, HttpResponse, Uri}

trait HttpMetrics[LoggingContext] {
  def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse)(implicit ctx: LoggingContext): Unit
}

object HttpMetrics {
  def none[LoggingContext]: HttpMetrics[LoggingContext] =
    new HttpMetrics[LoggingContext] {
      override def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse)(implicit ctx: LoggingContext): Unit = ()
    }
}
