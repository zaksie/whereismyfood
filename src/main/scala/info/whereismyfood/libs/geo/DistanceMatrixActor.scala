package info.whereismyfood.libs.geo

import akka.actor.Status.Failure
import akka.actor.{Actor, Props}
import com.google.maps.model.{DistanceMatrix, DistanceMatrixElementStatus, TravelMode, LatLng => GoogleLatLng}
import com.google.maps.DistanceMatrixApi
import info.whereismyfood.modules.geo
import info.whereismyfood.modules.geo.{Address, DistanceEx, LatLng,  DistanceMatrix => MyDistanceMatrix}


/**
  * Created by zakgoichman on 10/21/16.
  */
object DistanceMatrixActor {
  def props = Props[DistanceMatrixActor]
}

class DistanceMatrixActor extends Actor {
  val MAX_ALLOWABLE_LENGTH = 10
  import GoogleGeoAPIContext._

  override def receive: Receive = {
    case points: Seq[LatLng] =>
      //if(points.length > MAX_ALLOWABLE_LENGTH) throw new Exception("Exceeded maximum coordinate set count of " + MAX_ALLOWABLE_LENGTH)
      val groups = points.map(_.toGoogleLatLng).grouped(MAX_ALLOWABLE_LENGTH).toSeq
      val dm = MyDistanceMatrix()
      groups.foreach { from =>
        groups.foreach { to =>
          dm.merge(generateDistanceMatrix(from, to))
          if (from != to) dm.merge(generateDistanceMatrix(to, from))
        }
      }
      sender ! dm
    case _ => sender ! Failure(new Exception("Incorrect input to DistanceMatrixActor"))
  }

  def generateDistanceMatrix(from: Seq[GoogleLatLng], to: Seq[GoogleLatLng]):geo.DistanceMatrix = {
    val result = DistanceMatrixApi.newRequest(geoApiContext)
      .units(com.google.maps.model.Unit.METRIC)
      .origins(from:_*)
      .destinations(to:_*)
      .mode(TravelMode.WALKING)
      .await()
    parseResults(result, from.zipWithIndex, to.zipWithIndex)
  }
  def parseResults(result: DistanceMatrix, from: Seq[(GoogleLatLng, Int)], to: Seq[(GoogleLatLng, Int)]): geo.DistanceMatrix = {
    new geo.DistanceMatrix(from.flatMap { f =>
      to.flatMap { t =>
        val data = result.rows(f._2).elements(t._2)
        if (data.status != DistanceMatrixElementStatus.OK || f._1 == t._1) None
        else {
          val fromName = result.originAddresses(f._2)
          val toName = result.destinationAddresses(t._2)

          Option(new DistanceEx(Address(LatLng(f._1), fromName),
            Address(LatLng(t._1), toName),
            data.distance.inMeters,
            data.duration.inSeconds))
        }
      }
    })
  }
}
