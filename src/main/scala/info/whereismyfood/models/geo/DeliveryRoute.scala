package info.whereismyfood.models.geo

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/18/16.
  */
case class DeliveryRoute(id: Long)

object DeliveryRouteJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val deliveryRouteFormatter = jsonFormat1(DeliveryRoute)
}
