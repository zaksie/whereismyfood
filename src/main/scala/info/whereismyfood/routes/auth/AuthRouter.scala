package info.whereismyfood.routes.auth

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.libs.auth.Creds

/**
  * Created by zakgoichman on 10/20/16.
  */
object AuthRouter {
  def routes =
    pathPrefix("auth") {
      JwtApi.routes ~
      VerifyPhone.routes
    }
}
