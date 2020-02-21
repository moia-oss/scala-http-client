package io.moia.scalaHttpClient

import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait MockServer extends BeforeAndAfterEach with BeforeAndAfterAll { self: Suite =>

  val mockServerPort = 38080

  val mockServerHttpClientConfig: HttpClientConfig =
    HttpClientConfig(
      scheme = "http",
      isSecureConnection = false,
      host = "127.0.0.1",
      port = mockServerPort
    )

  private var clientAndServer: Option[ClientAndServer] = None

  override def beforeAll(): Unit = {
    super.beforeAll()
    clientAndServer.getOrElse {
      val newServerMock = startClientAndServer(mockServerPort)
      clientAndServer = Some(newServerMock)
      newServerMock
    }
    ()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    clientAndServer.foreach(_.reset())
  }

  override def afterAll(): Unit = {
    super.afterAll()
    clientAndServer.foreach(_.stop())
    clientAndServer = None
  }

  def getClientAndServer: ClientAndServer =
    clientAndServer.getOrElse(throw new IllegalStateException("Mock not started yet!"))
}
