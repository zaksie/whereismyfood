package info.whereismyfood.libs.geo

import akka.actor.{Actor, Props}
import com.google.gson.GsonBuilder
import com.google.maps.DirectionsApi.RouteRestriction
import com.google.maps.model.{LatLng, TravelMode}
import com.google.maps.{DirectionsApi, GeoApiContext}
import info.whereismyfood.aux.MyConfig

/**
  * Created by zakgoichman on 10/21/16.
  */

object RouteOptimizationActor {
  val geoApiContext = new GeoApiContext().setApiKey(MyConfig.get("google.apikey"));
  def props = Props[RouteOptimizationActor]
}

class RouteOptimizationActor extends Actor {
  val googleContext = RouteOptimizationActor.geoApiContext
  val gson = new GsonBuilder().setPrettyPrinting().create()
  override def receive: Receive = {
    case coords: List[(Double,Double)] => {
      val latlangs = coords map (c => new LatLng(c._1, c._2))
      val waypoints = latlangs.drop(1).map(ll => ll.lat + "," + ll.lng)
      val result = DirectionsApi.newRequest(googleContext)
        .mode(TravelMode.DRIVING)
        .avoid(RouteRestriction.TOLLS, RouteRestriction.FERRIES)
        .units(com.google.maps.model.Unit.METRIC)
        .origin(latlangs(0))
        .destination(latlangs(0))
        .optimizeWaypoints(true)
        .waypoints(waypoints: _*)
        .await()
      val json = gson.toJson(result)
      sender ! json
    }
    case _ => sender ! "this actors accepts only an array of doubles"
  }
}
