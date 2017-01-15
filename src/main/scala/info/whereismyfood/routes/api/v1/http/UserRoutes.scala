package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.device.DeviceInfoJson
import info.whereismyfood.modules.user.{Creds, GenericUser, UserRouter}
import org.slf4j.LoggerFactory
/**
  * Created by zakgoichman on 10/21/16.
  */
object UserRoutes {
  private val log = LoggerFactory.getLogger(this.getClass)

  def routes(implicit creds: Creds) = pathPrefix("users"){
    (path("is" / Segment) & post) { job =>
      complete {
        UserRouter.getByJob(job) match {
          case Some(userCompObj) =>
            userCompObj.handshake(creds)
          case _ =>
            400
        }
      }
    } ~
        path("me") {
          GenericUser.getById(creds.phone) match {
            case Some(user) => complete(user.getInfoJson)
            case _ => complete(503)
          }
        } ~
    pathPrefix("device"){
      pathPrefix("supports"){
        path("nfc"){
          import info.whereismyfood.modules.device.DeviceInfoJsonSupport._
          entity(as[DeviceInfoJson]){deviceInfo =>
            complete(deviceInfo.supportsNFC)
          }
        }
      }
    }
  }
}
