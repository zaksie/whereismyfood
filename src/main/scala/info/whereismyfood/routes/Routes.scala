package info.whereismyfood.routes

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.{CorsDirectives, CorsSettings}
import info.whereismyfood.routes.api.v1.ApiV1Router
import info.whereismyfood.routes.auth.AuthRouter

import scala.collection.immutable

/**
  * Created by zakgoichman on 10/20/16.
  */
object Routes {
  import HttpMethods._

  val settings = CorsSettings.defaultSettings.
    copy(allowGenericHttpRequests = true,
      allowedMethods = immutable.Seq(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS))

  def routes = handleRejections(CorsDirectives.corsRejectionHandler) {
    cors(settings) {
        pathEndOrSingleSlash {
          complete("Welcome to Yummlet API v.02")
        } ~
          AuthRouter.routes ~
          ApiV1Router.routes
    }
  }
}

