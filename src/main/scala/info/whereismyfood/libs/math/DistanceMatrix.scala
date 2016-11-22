package info.whereismyfood.libs.math

import info.whereismyfood.libs.geo.GeoMySQLInterface
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._

/**
  * Created by zakgoichman on 10/25/16.
  */


object DistanceMatrix {
  def getFromDBWithinRadius(locations: Seq[LatLng], radius_meter: Long = 50): Option[DistanceMatrix] = getFromMySQL(locations, radius_meter)

  private def getFromMySQL(locations: Seq[LatLng], radius_meter: Long): Option[DistanceMatrix] = {
    val dm = DistanceMatrix()
    locations.foreach { p1 =>
      locations.foreach {
        case p2 if p1 != p2 =>
          GeoMySQLInterface.findDistanceBetween(p1,p2, radius_meter).map{
            case Some(distance) =>
              dm.add(distance)
            case _ =>
          }
        case _ =>
      }
    }
    dm.toOption
  }
}
case class DistanceMatrix() {
  private val distances = new scala.collection.mutable.ArrayBuffer[Distance]

  def this(ds: Seq[Distance]) = {
    this
    add(ds:_*)
  }

  def add(ds: Distance*): DistanceMatrix = {
    for(d <- ds) {
      val hash = d.hashCode
      if (!distances.exists(_.hashCode == hash)) {
        distances.append(d)
      }
    }
    this
  }

  def getAll : Seq[Distance] = distances
  def get(from: LatLng, to: LatLng): Option[Distance] = distances.find(d => d.from == from && d.to == to)
  def size = {
    distances.map(_.from).distinct.length
  }

  def merge(dm2: DistanceMatrix): DistanceMatrix = {
    this.add(dm2.distances: _*)
    this
  }

  def toOption: Option[DistanceMatrix] = if (distances.isEmpty) None else Some(this)

  def saveToDB(): Unit = saveToMySQL()

  private def saveToMySQL(): Unit = {
    distances.foreach(_.saveToDB)
  }
}

