package info.whereismyfood.routes.auth

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.auth._
import info.whereismyfood.models.user.Roles._
import info.whereismyfood.models.user.{Creds, _}
import info.whereismyfood.modules.auth.VerifyPhoneModule

/**
  * Created by zakgoichman on 10/23/16.
  */
object Login extends Directives with AuthenticationHandler {
  import ActorSystemContainer.Implicits._
  import info.whereismyfood.models.user.CredsJsonSupport._

  private def success(user: GenericUser) = {
    val encodedToken = createToken(user)
    HttpResponse(StatusCodes.OK, entity = encodedToken)
  }

  def routes = (path("request-otp" / Segment) & post) {
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
      case "chef" =>
        entity(as[APIKey]) { apiKey =>
          complete {
            ChefUser.findAndVerify(apiKey) match {
              case Some(user) =>
               success(user)
              case None => 401
            }
          }
        }
      case "api" =>
        entity(as[APIKey]) { apiKey =>
          complete {
            APIUser.findAndVerify(apiKey) match {
              case Some(user) =>
                success(user)
              case None => 401
            }
          }
        }
    }

  /*
  ~
      (path("request-chef-otp") & post) {
        entity(as[MobileChefCreds]) { creds =>
          complete {
            RegisteredUser.find(creds.asTrustedCreds) match {
              case Some(account) =>
                Roles.isauthorized(manager) match {
                  case false => HttpResponse(StatusCodes.Unauthorized, entity = "Login credentials are invalid")
                  case true =>
                    val code = if (VerifyPhoneModule.requestOTPForChef(account, creds)) StatusCodes.OK else StatusCodes.Unauthorized
                    HttpResponse(code)
                }
              case _ => HttpResponse(StatusCodes.Unauthorized, entity = "Login credentials are invalid")
            }
          }
        }
      } ~
      (path("chef-otp-login") & post) {
        entity(as[MobileChefCreds]) { creds =>
          complete {
            RegisteredUser.find(creds.asTrustedCreds) match {
              case Some(account) =>
                val jwt = createToken {
                  RegisteredUser.createChefAccount(account, creds.asTrustedCreds)
                }
                HttpResponse(StatusCodes.OK, entity = s"""{"jwt":"$jwt"}""")
              case _ => HttpResponse(StatusCodes.Unauthorized, entity = "Login credentials are invalid")
            }
          }
        }
      } ~
      (path("request-otp") & post) {
        entity(as[String]) { phone =>
          val creds = Creds(phone)
          val code = if (VerifyPhoneModule.requestOTP(creds)) StatusCodes.OK else StatusCodes.Unauthorized
          complete(HttpResponse(code))
        }
      } ~
      (path("otp-login") & post) {
        entity(as[Creds]) { creds =>
          val result = VerifyPhoneModule.verifyExistingUser(creds.asTrustedCreds)
          complete {
            if (result.ok) HttpResponse(StatusCodes.OK, entity = result.toJson)
            else HttpResponse(StatusCodes.Unauthorized, entity = result.toJson)
          }
        }
      }
  }
  */
}
