package info.whereismyfood.routes.api.v1

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.mock.SayHello
import info.whereismyfood.routes.auth

/**
  * Created by zakgoichman on 10/20/16.
  */
object Index {
  def routes =
    pathPrefix("api" / "v1") {
      auth.JwtApi.checkJWT ~
      SayHello.routes ~
      OptRoute.routes
    }
}
