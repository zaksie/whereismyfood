package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.business.{Business, GetBusinessesNearMe}
import info.whereismyfood.modules.geo.Coords
import info.whereismyfood.modules.user.Creds
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 10/21/16.
  */
object BusinessRoutes{
  val log = LoggerFactory.getLogger(this.getClass)
  val businessActorRef = Await.result(system.actorSelection("/user/modules/business").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    pathPrefix("businesses") {
      (post & path("near")) {
        import info.whereismyfood.modules.geo.CoordsJsonSupport._
        entity(as[Coords]) { coords =>
          val res = Await.result(businessActorRef ? GetBusinessesNearMe(coords.toLatLng), 30 seconds).asInstanceOf[String]
          println("getting nearby business: " + res)
          complete(res)
        }
      } ~
      path(LongNumber){ id =>
          get{
            Business.get(id) match {
              case Seq() =>
                println("ERROR!")
                complete(404)
              case businesses =>
                import info.whereismyfood.modules.business.BusinessJsonSupport._
                println("OK!")
                println(businesses.head)
                val filteredBusiness = businesses.head.filterForRole(creds.role)
                complete(filteredBusiness.toJson.compactPrint)
            }
          } ~
        path("menu") {
          complete(404)
        }
      }
    }
  }
}
