package info.whereismyfood.modules.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/26/16.
  */
object OrderJsonFormatters {

  case class OpType_Orders_Json(`type`: String, orders: Seq[ProcessedOrder])

  case class SingleOpType_Json(`type`: String, orderId: String)

  object OpType_JsonFormatter extends DefaultJsonProtocol with SprayJsonSupport {
    import ProcessedOrderJsonSupport._
    implicit val opType_Orders_JsonFormatter = jsonFormat(OpType_Orders_Json, "type", "orders")
    implicit val singleOpType_JsonFormatter = jsonFormat(SingleOpType_Json, "type", "orderId")
  }
}