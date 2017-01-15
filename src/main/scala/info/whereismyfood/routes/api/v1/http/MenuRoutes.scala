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
      path("id" / LongNumber) { id =>
        get{
          getMenu(id)
        }
      } ~
      path("business" / LongNumber) { id =>
        get{
          getMenuCovers(id)
        }
      }
    }
  }

  def getMenuCovers(businessId: Long) ={
    get {
      println("In getMenuCovers")
      Menu.getRecordsByBusinessId(businessId) match {
        case Seq() =>
          complete(404)
        case menus =>
          complete(menus.map(_.toJson.compactPrint).mkString("[", ",", "]"))
      }
    }
  }

  def getMenu(id: Long) = {
    get {
      Menu.find(id) match {
        case Some(menu) =>
          complete(menu.toJson.compactPrint)
        case _ =>
          complete(404)
      }
    }
  }
}
