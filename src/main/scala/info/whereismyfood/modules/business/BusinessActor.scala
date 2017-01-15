package info.whereismyfood.modules.business

import akka.actor.{Actor, ActorLogging, Props}
import info.whereismyfood.modules.geo.{GeoMySQLInterface, LatLng}
import info.whereismyfood.modules.user.Roles
import spray.json._

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 12/23/16.
  */
case class GetBusinessesNearMe(latLng: LatLng)
object BusinessModule{
  def props = Props[BusinessActor]
}
class BusinessActor extends Actor with ActorLogging {
  override def receive = {
    case GetBusinessesNearMe(position) =>
      import info.whereismyfood.modules.business.BusinessJsonSupport._
      val res = GeoMySQLInterface.findBusinessesNearMe(position)(50000*1000) map(_.map(_.filterForRole(Roles.client)).toJson.compactPrint)
      sender ! Await.result[String](res, 30 seconds)
  }
}
