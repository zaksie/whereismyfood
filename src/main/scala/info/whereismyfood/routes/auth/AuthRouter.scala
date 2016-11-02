package info.whereismyfood.routes.auth

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.http.HTTP
import info.whereismyfood.routes.api.v1.ws.WS
import info.whereismyfood.routes.auth

/**
  * Created by zakgoichman on 10/20/16.
  */
object AuthRouter {
  val routes =
    pathPrefix("auth") {
      JwtApi.routes ~
      VerifyPhone.routes
    }
}
