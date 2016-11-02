package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.auth.VerifyPhone

/**
  * Created by zakgoichman on 10/20/16.
  */
object HTTP {
  val routes =
    VerifyPhone.routes ~
    OptRoute.routes
}
