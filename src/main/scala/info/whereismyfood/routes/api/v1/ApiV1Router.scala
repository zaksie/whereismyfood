package info.whereismyfood.routes.api.v1

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.http.HTTP
import info.whereismyfood.routes.api.v1.ws.WS
import info.whereismyfood.routes.auth

/**
  * Created by zakgoichman on 10/20/16.
  */
object ApiV1Router {
  val routes =
    pathPrefix("api" / "v1") {
      auth.JwtApi.checkJWT ~
        WS.routes ~
        HTTP.routes
    }
}
