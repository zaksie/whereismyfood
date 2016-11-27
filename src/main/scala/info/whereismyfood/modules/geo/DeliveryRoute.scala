package info.whereismyfood.modules.geo

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.ask
import com.google.maps.internal.PolylineEncoding
import com.google.maps.model.EncodedPolyline
import info.whereismyfood.libs.geo.GetPolyline
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
/**
  * Created by zakgoichman on 11/18/16.
  */

object DeliveryRoute{
  import concurrent.ExecutionContext.Implicits.global
  def empty = DeliveryRoute("")
  def of(a: LatLng, b: LatLng): Option[DeliveryRoute] = {
    import info.whereismyfood.aux.ActorSystemContainer.Implicits._
    val f = GeoMySQLInterface.findRoute(a, b).flatMap {
      case Some(polyline) =>
        Future.successful(Some(DeliveryRoute(polyline)))
      case _ =>
        system.actorSelection("/user/libs/google-directions-api").resolveOne().flatMap { aref =>
          aref ? GetPolyline(a, b) map {
            case x: EncodedPolyline =>
              //log.info("in Address.of with result: {}", result)
              GeoMySQLInterface.savePolyline(a,b,x)
              Some(DeliveryRoute(x.getEncodedPath))
            case x =>
              println(x)
              None
          }
        }
    }

    Await.result(f, 10 seconds)
  }

  def of(points: Seq[LatLng]) = encode(points)
  def encode(points: Seq[LatLng]) = DeliveryRoute(PolylineEncoding.encode(points.map(_.toGoogleLatLng).asJava))
}

case class DeliveryRoute(polyline: String){
  def decode: Seq[LatLng] = PolylineEncoding.decode(polyline).asScala.map(LatLng.apply)
}

object DeliveryRouteJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val deliveryRouteFormatter = jsonFormat1(DeliveryRoute.apply)
}
