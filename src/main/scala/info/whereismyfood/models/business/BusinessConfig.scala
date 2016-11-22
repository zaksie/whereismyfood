package info.whereismyfood.models.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/17/16.
  */
object BusinessConfig extends DefaultJsonProtocol with SprayJsonSupport{
  val default = BusinessConfig(15*60)
  implicit val formatter = jsonFormat1(BusinessConfig.apply)
}
case class BusinessConfig(orderTimeConstraints_sec: Long)

