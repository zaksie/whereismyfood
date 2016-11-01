package info.whereismyfood.routes.auth

import info.whereismyfood.libs.auth.{APIKey, AuthenticationHandler, JwtApiHandler}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
/**
  * Created by zakgoichman on 10/23/16.
  */
object JwtApi extends Directives with JwtApiHandler with AuthenticationHandler {
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  val routes = {
    post {
      path("login") {
        entity(as[APIKey]) { apikey =>
          val key = apikey.apikey
          complete {
            getLoginInfo(key) match {
              case Some(loginEntity) => {
                val encodedToken = createToken(key)
                HttpResponse(StatusCodes.OK, entity = encodedToken)
              }
              case None => HttpResponse(StatusCodes.Unauthorized, entity = "login credentials are invalid")
            }
          }
        }
      }
    }
  }
}
