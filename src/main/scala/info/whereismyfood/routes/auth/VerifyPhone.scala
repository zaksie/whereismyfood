package info.whereismyfood.routes.auth

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.libs.auth.Creds
import info.whereismyfood.modules.VerifyPhoneModule
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/21/16.
  */
object VerifyPhone {

  import info.whereismyfood.libs.auth.CredsJsonSupport._

  val log = LoggerFactory.getLogger(this.getClass)

  def routes =
    path("request-verify-phone") {
    post {
      entity(as[Creds]) { creds =>
        val code = if (VerifyPhoneModule.requestVerificationCode(creds)) StatusCodes.OK else StatusCodes.Forbidden
        complete(HttpResponse(code))
      }
    }
  } ~
    path("verify-phone") {
      post {
        entity(as[Creds]) { creds =>
          val result = VerifyPhoneModule.verify(creds)
          complete {
            if (result.ok) HttpResponse(StatusCodes.OK, entity = result.toJson)
            else HttpResponse(StatusCodes.Unauthorized, entity = result.toJson)
          }
        }
      }
    }
}


