package routes.api.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import akka.pattern.ask
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/21/16.
  */
object OptRoute {
  implicit val resolveTimeout = Timeout(30 seconds)
  val log = LoggerFactory.getLogger(this.getClass)
  def routes(implicit system: ActorSystem) = {
    path("optroute") {
      get {
        parameter('coords.as[String].*) { coords =>
          val latlngs = coords.map {
            s => s.split(",") match {
              case Array(x: String, y: String) => Some((x.toDouble, y.toDouble))
              case _ => None
            }
          }.flatten

          if (latlngs.isEmpty) {
            complete("Invalid values")
          }
          else {
            val actorRef = Await.result(system.actorSelection("/user/optroute").resolveOne(), resolveTimeout.duration)
            val result = Await.result(actorRef ? latlngs, resolveTimeout.duration)

            complete(result.toString)
          }
        }
      }
    }
  }
}
