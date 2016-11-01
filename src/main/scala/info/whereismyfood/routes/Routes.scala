package routes

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._

/**
  * Created by zakgoichman on 10/20/16.
  */
object Routes {
  def routes(implicit as: ActorSystem) = {
    pathEndOrSingleSlash {
      complete("Welcome to whereismyfood.info API")
    } ~ api.v1.Index.routes
  }
}
