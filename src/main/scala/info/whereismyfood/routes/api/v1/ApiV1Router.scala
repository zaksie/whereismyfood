package info.whereismyfood.routes.api.v1

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.http.HTTP
import info.whereismyfood.routes.api.v1.ws.WS
import info.whereismyfood.routes.auth

import scala.concurrent.duration.FiniteDuration

/**
  * Created by zakgoichman on 10/20/16.
  */
object ApiV1Router {
  def routes =
      pathPrefix("api" / "v1") {
        auth.Login.checkJwt { implicit account =>
          WS.routes ~
            HTTP.routes
        }
      }
}
