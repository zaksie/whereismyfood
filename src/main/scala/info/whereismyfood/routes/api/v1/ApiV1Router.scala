package info.whereismyfood.routes.api.v1

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.http.HTTP
import info.whereismyfood.routes.api.v1.ws.WS
import info.whereismyfood.routes.auth.AuthenticationHandler
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
/**
  * Created by zakgoichman on 10/20/16.
  */
object ApiV1Router extends AuthenticationHandler {
  def routes =
    pathPrefix("api" / "v1") {
      authenticateOAuth2Async("", checkCredentials) { implicit creds =>
        HTTP.routes
      } ~
      parameter('token ? "") { token =>
        checkJwtToken(token) match {
          case (creds, 200, _) if creds.isDefined =>
            WS.routes(creds.get)
          case _ =>
            complete(401)
        }
      }
    }
}

