package info.whereismyfood

import akka.http.scaladsl.Http
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import info.whereismyfood.routes.Routes
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/21/16.
  */
object WebServer {
  def main(args: Array[String]) {

    val log = LoggerFactory.getLogger(this.getClass)
    val port = MyConfig.getInt("server.port");

    implicit val system = ActorSystemContainer.getSystem
    implicit val materializer = ActorSystemContainer.getMaterializer
    implicit val executionContext = system.dispatcher

    val bindingFuture = Http().bindAndHandle(Routes.routes, "localhost", port)

    println(s"Server online at http://localhost:"+port+"/\nPress RETURN to stop...")
    bindingFuture.onFailure {
      case ex: Exception =>
        log.error("Failed to bind to port " + port, ex)
        System.exit(1)
    }
  }
}
