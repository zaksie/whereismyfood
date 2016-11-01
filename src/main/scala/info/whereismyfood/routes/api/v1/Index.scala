package routes.api.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import routes.api.v1.mock.SayHello

/**
  * Created by zakgoichman on 10/20/16.
  */
object Index {
  def routes(implicit as: ActorSystem) =
    pathPrefix("api" / "v1") {
      SayHello.routes ~
      OptRoute.routes
    }
}
