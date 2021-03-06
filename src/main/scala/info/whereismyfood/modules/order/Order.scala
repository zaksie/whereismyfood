package info.whereismyfood.modules.order

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.menu.DishToAdd
import info.whereismyfood.modules.user.Creds
import spray.json.DefaultJsonProtocol


/**
  * Created by zakgoichman on 11/8/16.
  */

case class Order(id: String, businessId: Long, client: Creds,
                 deliveryMode: DeliveryMode = DeliveryModes.NA, contents: Seq[DishToAdd]) {
  val timestamp: Long = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond
}

object OrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.modules.user.CredsJsonSupport._
  import info.whereismyfood.modules.menu.DishJsonSupport._
  import DeliveryModeJsonSupport._
  implicit val newOrderFormat = jsonFormat(Order, "id", "businessId", "client", "deliveryMode", "contents")
}