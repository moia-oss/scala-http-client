package io.moia.scalaHttpClient

import akka.http.scaladsl.model.{HttpMethod, HttpResponse, Uri}

trait HttpMetrics {
  def meterResponse(method: HttpMethod, path: Uri.Path, response: HttpResponse): Unit
}
