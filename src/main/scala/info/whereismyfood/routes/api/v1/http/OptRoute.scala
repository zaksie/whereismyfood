package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams
import info.whereismyfood.models.user.Creds
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._

/**
  * Created by zakgoichman on 10/21/16.
  */
object OptRoute {
  implicit val resolveTimeout = Timeout(300 seconds)
  val log = LoggerFactory.getLogger(this.getClass)
  val actorRef = Await.result(system.actorSelection("/user/modules/optroute").resolveOne(), resolveTimeout.duration)
  def routes(implicit creds: Creds) =
    path("optroute") {
      get {
        complete(200)
        /*parameters('start, 'destinations).as(DistanceMatrixRequestParams) { dmrp =>
          val result = Await.result(actorRef ? dmrp, resolveTimeout.duration)
          complete(gson.toJson(result))
        }*/
      }
    }
}
