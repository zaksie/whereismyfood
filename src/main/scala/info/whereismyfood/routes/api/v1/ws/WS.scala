package info.whereismyfood.routes.api.v1.ws

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.http.OptRoute
import info.whereismyfood.routes.auth

/**
  * Created by zakgoichman on 10/20/16.
  */
object WS {
  val routes =
    pathPrefix("ws") {
      Tracking.routes
    }
}
