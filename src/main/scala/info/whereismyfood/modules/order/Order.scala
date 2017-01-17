package info.whereismyfood.modules.order

import java.time.{ZoneOffset, ZonedDateTime}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.menu.DishToAdd
import info.whereismyfood.modules.user.Creds
import spray.json.DefaultJsonProtocol


/**
  * Created by zakgoichman on 11/8/16.
  */
case class Order(id: String, businessId: Long, client: Creds, deliveryMode: String, contents: Seq[DishToAdd]) {
  val timestamp: Long = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond
}

object OrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.modules.user.CredsJsonSupport._
  import info.whereismyfood.modules.menu.DishJsonSupport._
  implicit val newOrderFormat = jsonFormat(Order, "id", "businessId", "client", "deliveryMode", "contents")
}