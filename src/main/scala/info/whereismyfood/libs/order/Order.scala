package info.whereismyfood.libs.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.libs.auth.Creds
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/8/16.
  */
case class Order(recipient: Creds, courier: Creds, contents: OrderContents)

object OrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.libs.auth.CredsJsonSupport._
  implicit val priceFormat = jsonFormat3(Price)
  implicit val itemFormat = jsonFormat(OrderItem, "name","image", "description", "notes", "price")
  implicit val contentsFormat = jsonFormat(OrderContents, "items")
  implicit val orderFormat = jsonFormat(Order, "recipient", "courier", "contents")
}