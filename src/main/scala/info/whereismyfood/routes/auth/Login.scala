package info.whereismyfood.routes.auth

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.modules.user._

/**
  * Created by zakgoichman on 10/23/16.
  */
object Login extends Directives with AuthenticationHandler {
  import ActorSystemContainer.Implicits._
  import info.whereismyfood.modules.user.CredsJsonSupport._

  private def success(user: GenericUser) = {
    val encodedToken = createTokenFromUser(user)
    HttpResponse(StatusCodes.OK, entity = encodedToken)
  }

  def routes =
    (path("request-otp" / Segment) & post) { job =>
      println("job: " + job)
      entity(as[Creds]) { creds =>
        complete {
          UserRouter.getByJob(job) match {
            case Some(factory) =>
              factory.requestOTP(creds.phone)
            case _ =>
              401
          }
        }
      }
    } ~
    (path("otp-login" / Segment) & post) { job =>
        entity(as[Creds]) { creds =>
          complete {
            UserRouter.getByJob(job) match {
              case Some(factory) =>
                factory.verifyOTP(creds) match {
                  case Some(user) => user.jwt
                  case _ => 401
                }
              case _ =>
                401
            }
          }
        }
    } ~
    (path("key-login" / Segment) & post) { job =>
      entity(as[APIKey]) { apiKey =>
        complete {
          UserRouter.getByJob(job) match {
            case Some(factory) =>
              factory.findAndVerify(apiKey) match {
                case Some(user) =>
                  success(user)
                case _ => 401
              }
            case _ => 401
          }
        }
      }
    }
}
