package info.whereismyfood.modules.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/17/16.
  */
object BusinessConfig extends DefaultJsonProtocol with SprayJsonSupport{
  val default = BusinessConfig(2*60)
  implicit val formatter = jsonFormat1(BusinessConfig.apply)
}
case class BusinessConfig(orderTimeConstraints_sec: Long)

