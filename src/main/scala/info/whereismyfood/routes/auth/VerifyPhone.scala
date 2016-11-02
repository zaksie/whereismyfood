package info.whereismyfood.routes.auth

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.libs.auth.Account
import info.whereismyfood.modules.VerifyPhoneModule
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/21/16.
  */
object VerifyPhone {

  import info.whereismyfood.libs.auth.AccountJsonSupport._

  val log = LoggerFactory.getLogger(this.getClass)

  val routes = path("request-verify-phone") {
      post {
        entity(as[Account]) { account =>
          VerifyPhoneModule.requestVerificationCode(account)
          complete(HttpResponse(StatusCodes.OK))
        }
      }
    } ~
      path("verify-phone") {
        post {
          entity(as[Account]) { account =>
            val ok = VerifyPhoneModule.verify(account)
            complete(if (ok) HttpResponse(StatusCodes.OK) else HttpResponse(StatusCodes.Unauthorized))
          }
        }
      }
}



