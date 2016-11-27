package info.whereismyfood.modules.order

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
object OrderError{
  val OK = OrderError()
}
case class OrderError(error: String = ""){
  def ok: Boolean = error.isEmpty
}

case class Orders(businessId: Long, orders: Seq[Order]) extends OrderCommon{
  override def getBusinessId: Long = businessId
  def isValid: OrderError ={
    orders.find(x=>x.client.phone.isEmpty || x.client.geoaddress.isEmpty) match {
      case Some(faultyOrder) =>
        if(faultyOrder.client.phone.isEmpty) OrderError(s"Phone field missing for order ${faultyOrder.id}")
        else OrderError(s"Address field either missing or couldn't be geocoded for order ${faultyOrder.id}")
      case _ =>
        OrderError.OK
    }
  }
}

case class OrderReady(orderId: String, ready: Boolean)

object OrdersJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import OrderJsonSupport._
  implicit val orderErrorFormatter = jsonFormat1(OrderError.apply)
  implicit val orderReadyFormat = jsonFormat2(OrderReady)
  implicit val ordersFormat = jsonFormat(Orders, "businessId", "orders")
}