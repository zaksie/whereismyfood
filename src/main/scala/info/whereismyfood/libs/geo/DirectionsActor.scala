package info.whereismyfood.libs.geo

import akka.actor.Status.Failure
import akka.actor.{Actor, Props}
import com.google.maps.model.{DirectionsResult, EncodedPolyline, TravelMode, LatLng => GoogleLatLng}
import com.google.maps.DirectionsApi
import info.whereismyfood.modules.geo.LatLng


/**
  * Created by zakgoichman on 10/21/16.
  */
case class GetPolyline(a: LatLng, b: LatLng)
object DirectionsActor {
  def props = Props[DirectionsActor]
}

class DirectionsActor extends Actor {
  val MAX_ALLOWABLE_LENGTH = 10
  import GoogleGeoAPIContext._

  override def receive: Receive = {
    case GetPolyline(a, b) =>
      sender ! getPolyline(a.toGoogleLatLng,b.toGoogleLatLng)
    case _ => sender ! Failure(new Exception("Incorrect input to DirectionsActor"))
  }

  def getPolyline(from: GoogleLatLng, to: GoogleLatLng): EncodedPolyline = {
    val result = DirectionsApi.newRequest(geoApiContext)
            .origin(from)
            .destination(to)
        .units(com.google.maps.model.Unit.METRIC)
        .mode(TravelMode.WALKING)
        .await()
    parseResults(result, from, to)
  }
  def parseResults(result: DirectionsResult, from: GoogleLatLng, to: GoogleLatLng): EncodedPolyline = {
    result.routes(0).overviewPolyline
  }
}
