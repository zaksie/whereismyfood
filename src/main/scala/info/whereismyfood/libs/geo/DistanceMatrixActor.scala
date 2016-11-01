package info.whereismyfood.libs.operations

import akka.actor.{Actor, Props}
import com.google.gson.GsonBuilder
import com.google.maps.DirectionsApi.RouteRestriction
import com.google.maps.model.{LatLng, TravelMode}
import com.google.maps.{DistanceMatrixApi, GeoApiContext}
import info.whereismyfood.aux.MyConfig

import scala.util.matching.Regex

/**
  * Created by zakgoichman on 10/21/16.
  */

object DistanceMatrixActor {
  val geoApiContext = new GeoApiContext().setApiKey(MyConfig.get("google.apikey"));
  def props = Props(new DistanceMatrixActor)
}
case class DistanceMatrixRequestParams(start: String, destinations: String)

class DistanceMatrixActor extends Actor {
  val MAX_ALLOWABLE_LENGTH = 10
  val goeApiContext = DistanceMatrixActor.geoApiContext
  val gson = new GsonBuilder().setPrettyPrinting().create()
  val coordInputPattern = new Regex("""\((.*?)\)""")

  override def receive: Receive = {
    case params: DistanceMatrixRequestParams => {
      val points = getLatLngs(params.destinations)
      val start = getLatLngs(params.start)
      val all = points ++ start
      if(all.length > MAX_ALLOWABLE_LENGTH) throw new Exception("Exceeded maximum coordinate set count of " + MAX_ALLOWABLE_LENGTH)
      val result = DistanceMatrixApi.newRequest(goeApiContext)
          .units(com.google.maps.model.Unit.METRIC)
          .origins(all:_*)
          .destinations(points:_*)
          .mode(TravelMode.DRIVING)
          .avoid(RouteRestriction.TOLLS)
          .await()
      val json = gson.toJson(result)
      sender ! json
    }
    case _ => sender ! "this actor accepts DistanceMatrixRequestParams"
  }

  def getLatLngs(s: String): List[LatLng] ={
    val points = coordInputPattern.findAllIn(s).matchData.map[LatLng]{
      m =>
        val s = m.group(1).split(",")
        new LatLng(s(0).toDouble, s(1).toDouble)
    }

    points.toList
  }
}
