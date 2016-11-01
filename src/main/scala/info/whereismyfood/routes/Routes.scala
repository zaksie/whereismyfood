package info.whereismyfood.routes

import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.CorsDirectives._

/**
  * Created by zakgoichman on 10/20/16.
  */
object Routes {
  def routes = cors() {
    auth.JwtApi.routes ~
      pathEndOrSingleSlash {
        complete("Welcome to whereismyfood.info API")
      } ~
      api.v1.Index.routes
  }
}
