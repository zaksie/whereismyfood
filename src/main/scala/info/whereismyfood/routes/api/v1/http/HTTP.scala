package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.models.user._
/**
  * Created by zakgoichman on 10/20/16.
  */
object HTTP {
  def routes(implicit creds: Creds) =
    OptRoute.routes ~
      OrderRoutes.routes ~
      ManagerRoutes.routes ~
      (pathEndOrSingleSlash & get) {
        complete(200)
      } ~
      (path("is" / Segment) & post) { job =>
        complete {
          UserRouter.getByJob(job) match {
            case Some(userCompObj) =>
              userCompObj.handshake(creds)
            case _ =>
              400
          }
        }
      }
}
