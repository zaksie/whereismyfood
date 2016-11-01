package info.whereismyfood.routes.api.v1

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/21/16.
  */
object OptRoute {
  implicit val resolveTimeout = Timeout(300 seconds)
  val log = LoggerFactory.getLogger(this.getClass)
  val system = ActorSystemContainer.getSystem
  val actorRef = Await.result(system.actorSelection("/user/modules/optroute").resolveOne(), resolveTimeout.duration)

  def routes = {
    path("optroute") {
      get {
        parameters('start, 'destinations).as(DistanceMatrixRequestParams) { dmrp =>
          val result = Await.result(actorRef ? dmrp, resolveTimeout.duration)
          complete(result.toString)
        }
      }
    }
  }
}
