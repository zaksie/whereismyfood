package info.whereismyfood.libs.geo

import akka.actor.Status.Failure
import akka.actor.{Actor, Props}
import com.google.maps.model.{GeocodingResult}
import com.google.maps.{GeoApiContext, GeocodingApi}
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.libs.math.{LatLng}


/**
  * Created by zakgoichman on 10/21/16.
  */
case class AddressToLatLng(address: String)

object GeocodingActor {
  val geoApiContext = new GeoApiContext().setApiKey(MyConfig.get("google.apikey"))
  def props = Props[GeocodingActor]
}

class GeocodingActor extends Actor {
  val MAX_ALLOWABLE_LENGTH = 10
  import GeocodingActor._

  override def receive: Receive = {
    case location: LatLng =>
      val results: Seq[GeocodingResult] =  GeocodingApi.reverseGeocode(geoApiContext, location.toGoogleLatLng).await()
      sender ! results
    case AddressToLatLng(address) =>
      val results: Seq[GeocodingResult] =  GeocodingApi.geocode(geoApiContext, address).await();
      sender ! results
    case _ => sender ! Failure(new Exception("Incorrect input to GeocodingActor"))
  }
}