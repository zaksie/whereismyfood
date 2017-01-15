package info.whereismyfood.modules.device

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 1/15/17.
  */
case class DeviceInfoJson(brand: String, name: String, manufacturer : String, model: String) {
  def supportsNFC = true
}

object DeviceInfoJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val deviceInfoJsonFormat = jsonFormat4(DeviceInfoJson)
}
