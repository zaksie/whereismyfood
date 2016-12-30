package info.whereismyfood.modules.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.geo.LatLng
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 12/23/16.
  */
case class BusinessPublic(id: Long, name: String, image: String, rating: Double, mainMenu: String, latLng: LatLng)

object BusinessPublicJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import LatLng.latlngFormatter
  implicit val businessPublicFormatter = jsonFormat6(BusinessPublic)
}