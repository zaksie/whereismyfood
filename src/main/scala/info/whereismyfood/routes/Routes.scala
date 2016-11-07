package info.whereismyfood.routes

import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.{CorsDirectives, CorsSettings}
import info.whereismyfood.routes.api.v1.ApiV1Router
import info.whereismyfood.routes.auth.AuthRouter

/**
  * Created by zakgoichman on 10/20/16.
  */
object Routes {
  val settings = CorsSettings.defaultSettings.copy(allowGenericHttpRequests = true)
  def routes = handleRejections(CorsDirectives.corsRejectionHandler) {
    cors(settings) {
      AuthRouter.routes ~
        pathEndOrSingleSlash {
          complete("Welcome phone whereismyfood.info API")
        } ~
        ApiV1Router.routes
    }
  }
}

