package info.whereismyfood.routes.auth

import info.whereismyfood.libs.auth._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
/**
  * Created by zakgoichman on 10/23/16.
  */
object JwtApi extends Directives with JwtApiHandler with AuthenticationHandler {
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  import CredsJsonSupport._

  def routes = {
    post {
      path("login") {
        entity(as[APIKey]) { p =>
          complete {
            getLoginInfo(p.apikey) match {
              case Some(loginEntity) => {
                val encodedToken = createToken(p.apikey)
                HttpResponse(StatusCodes.OK, entity = encodedToken)
              }
              case None => HttpResponse(StatusCodes.Unauthorized, entity = "login credentials are invalid")
            }
          }
        }
      } ~
        path("mobile-login") {
          entity(as[Creds]) { creds =>
            complete {
              DatabaseAccount.find(creds) match {
                case Some(account) => {
                  val jwt = createToken(account)
                  HttpResponse(StatusCodes.OK, entity = s"""{"jwt":"$jwt"}""")
                }
                case _ => HttpResponse(StatusCodes.Unauthorized, entity = "Login credentials are invalid")
              }
            }
          }
        }
    }
  }
}
