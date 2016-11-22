package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.models.user.Creds
/**
  * Created by zakgoichman on 10/20/16.
  */
object HTTP {
  def routes(implicit creds: Creds) =
    OptRoute.routes ~
    OrderRoutes.routes ~
    ManagerRoutes.routes ~
    get{
      complete(200)
    }
}
