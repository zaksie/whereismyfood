package info.whereismyfood.routes.auth

import akka.http.scaladsl.server.Directives._

/**
  * Created by zakgoichman on 10/20/16.
  */
object AuthRouter {
  def routes =
    pathPrefix("auth") {
      Login.routes
    }
}
