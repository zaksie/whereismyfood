package info.whereismyfood.routes.auth

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.business.Business.Jobs
import info.whereismyfood.modules.user._
import info.whereismyfood.modules.user._

/**
  * Created by zakgoichman on 10/23/16.
  */
object Login extends Directives with AuthenticationHandler {
  import ActorSystemContainer.Implicits._
  import info.whereismyfood.modules.user.CredsJsonSupport._

  private def success(user: GenericUser) = {
    val encodedToken = createToken(user)
    HttpResponse(StatusCodes.OK, entity = encodedToken)
  }

  def routes =
    (path("request-otp" / Segment) & post) {
    case "manager" =>
      entity(as[String]) { phone =>
        complete {
          ManagerUser.find(phone) match {
            case Some(user) =>
              user.requestOTP
              HttpResponse(StatusCodes.OK)
            case None => 401
          }
        }
      }
  } ~
    (path("otp-login" / Segment) & post) {
      case "manager" =>
        entity(as[Creds]) { creds =>
          complete {
            ManagerUser.find(creds) match {
              case Some(user) if user.verifyOTP =>
                success(user)
              case _ => 401
            }
          }
        }
    } ~
    (path("key-login" / Segment) & post) {
      case Jobs.chefs =>
        println("In chefs...")
        entity(as[APIKey]) { apiKey =>
          complete {
            ChefUser.findAndVerify(apiKey) match {
              case Some(user) =>
               success(user)
              case None => 401
            }
          }
        }
      case Jobs.apiers =>
        entity(as[APIKey]) { apiKey =>
          complete {
            APIUser.findAndVerify(apiKey) match {
              case Some(user) =>
                success(user)
              case None => 401
            }
          }
        }
      case _ => complete(400, "Incorrect business job")
    }
}
