package info.whereismyfood.models.order

import java.time.{ZoneOffset, ZonedDateTime}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.models.user.Creds
import spray.json.DefaultJsonProtocol


/**
  * Created by zakgoichman on 11/8/16.
  */
case class Order(id: String, client: Creds, contents: Seq[OrderItem]) {
  val timestamp: Long = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond
}

object OrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.models.user.CredsJsonSupport._
  implicit val priceFormat = jsonFormat3(Price)
  implicit val itemFormat = jsonFormat(OrderItem, "name","image", "description", "notes", "price")
  implicit val orderFormat = jsonFormat(Order.apply, "id", "client", "contents")
}