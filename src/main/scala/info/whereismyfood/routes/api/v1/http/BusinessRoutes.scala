package info.whereismyfood.routes.api.v1.http

import java.io.FileNotFoundException

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.business.GetBusinessesNearMe
import info.whereismyfood.modules.geo.Coords
import info.whereismyfood.modules.user.{Creds, Roles}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import info.whereismyfood.routes.api.v1.http.templates.MenuTemplate
/**
  * Created by zakgoichman on 10/21/16.
  */
object BusinessRoutes {
  val log = LoggerFactory.getLogger(this.getClass)
  val businessActorRef = Await.result(system.actorSelection("/user/modules/business").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    import Roles.api.business._
    pathPrefix("businesses") {
      (post & path("near")) {
        import info.whereismyfood.modules.geo.CoordsJsonSupport._
        entity(as[Coords]) { coords =>
          val res = Await.result(businessActorRef ? GetBusinessesNearMe(coords.toLatLng), 30 seconds).asInstanceOf[String]
          println("getting nearby business: " + res)
          complete(res)
        }
      } ~
      (get & path("menu" / LongNumber)) { id =>
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,MenuTemplate(id).html))
      }
    }
  }
}
