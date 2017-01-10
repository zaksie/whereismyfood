package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.menu.Menu
import info.whereismyfood.modules.menu.MenuJsonSupport._
import info.whereismyfood.modules.user.Creds
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.Await
/**
  * Created by zakgoichman on 10/21/16.
  */
object MenuRoutes {
  private val log = LoggerFactory.getLogger(this.getClass)
  private val businessActorRef = Await.result(system.actorSelection("/user/modules/business").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    pathPrefix("menus") {
      path(LongNumber) { id =>
        getMenu(id) ~
        put {
          entity(as[Menu]) { menu =>
            if(Menu.addToDatastore(id, menu))
              complete(200)
            else complete(400)
          }
        }
      }
    }
  }

  def getMenu(id: Long) = {
    get {
      Menu.get(id) match {
        case Some(menu) =>
          complete(menu.toJson.compactPrint)
        case _ =>
          complete(404)
      }
    }
  }
}
