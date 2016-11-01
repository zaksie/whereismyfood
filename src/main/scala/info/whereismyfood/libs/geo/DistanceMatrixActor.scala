package info.whereismyfood.libs.geo

import akka.actor.{Actor, Props}
import com.google.gson.GsonBuilder
import com.google.maps.DirectionsApi.RouteRestriction
import com.google.maps.model.{DistanceMatrix, DistanceMatrixElementStatus, LatLng, TravelMode}
import com.google.maps.{DistanceMatrixApi, GeoApiContext}
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.libs.math.{Distance, Location, DistanceMatrix => MyDistanceMatrix}

import scala.util.Try
import scala.util.matching.Regex

/**
  * Created by zakgoichman on 10/21/16.
  */

object DistanceMatrixActor {
  val geoApiContext = new GeoApiContext().setApiKey(MyConfig.get("google.apikey"));
  def props = Props[DistanceMatrixActor]
}
case class DistanceMatrixRequestParams(start: String, destinations: String)

class DistanceMatrixActor extends Actor {
  val MAX_ALLOWABLE_LENGTH = 10
  val goeApiContext = DistanceMatrixActor.geoApiContext

  override def receive: Receive = {
    case points: Seq[LatLng] => {
      if(points.length > MAX_ALLOWABLE_LENGTH) throw new Exception("Exceeded maximum coordinate set count of " + MAX_ALLOWABLE_LENGTH)
      val result = DistanceMatrixApi.newRequest(goeApiContext)
          .units(com.google.maps.model.Unit.METRIC)
          .origins(points:_*)
          .destinations(points:_*)
          .mode(TravelMode.DRIVING)
          .avoid(RouteRestriction.TOLLS)
          .await()
      val distanceMatrixStruct = generateDistanceMatrixStruct(result, points)
      sender ! distanceMatrixStruct
    }
    case _ => sender ! "this actor accepts DistanceMatrixRequestParams"
  }

  def generateDistanceMatrixStruct(result: DistanceMatrix, points: Seq[LatLng]): MyDistanceMatrix = {
    var i = -1
    new MyDistanceMatrix(result.rows.flatMap{ from =>
      var j = 0
      i += 1
      from.elements.flatMap { to =>
          val fromName = result.originAddresses(i)
          val fromLoc = Location(fromName, points(i))

          val toName = result.destinationAddresses(j)
          val toLoc = Location(toName, points(j))
          j += 1
        if (i == j - 1 || to.status != DistanceMatrixElementStatus.OK) None
        else Option(Distance(fromLoc, toLoc, to.distance.inMeters, to.duration.inSeconds))
      }
    })
  }
}
