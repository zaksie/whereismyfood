package info.whereismyfood.routes

import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.{CorsDirectives, CorsSettings}
import info.whereismyfood.routes.api.v1.Index

/**
  * Created by zakgoichman on 10/20/16.
  */
object Routes {
  val settings = CorsSettings.defaultSettings.copy(allowGenericHttpRequests = true)
  def routes = handleRejections(CorsDirectives.corsRejectionHandler) {
    cors(settings) {
      auth.JwtApi.routes ~
        pathEndOrSingleSlash {
          complete("Welcome to whereismyfood.info API")
        } ~
        Index.routes
    }
  }
}

