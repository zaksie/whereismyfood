package info.whereismyfood.models.order

import java.time.ZonedDateTime

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/8/16.
  */
trait OrderCommon {
  val timestamp = ZonedDateTime.now.toString
  def getBusinessId: Long
}

case class Orders(businessId: Long, orders: Seq[Order]) extends OrderCommon{
  override def getBusinessId: Long = businessId
  def isValid: Boolean =
    orders.forall(x => x.client.phone.nonEmpty && x.client.address.isDefined)
}

case class OrderReady(ready: Boolean)

object OrdersJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import OrderJsonSupport._
  implicit val orderReadyFormat = jsonFormat1(OrderReady)
  implicit val ordersFormat = jsonFormat(Orders, "businessId", "orders")
}