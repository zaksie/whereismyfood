package info.whereismyfood.libs.opres.cvrp

/**
  * Created by zakgoichman on 10/24/16.
  */

import info.whereismyfood.libs.math.{DistanceMatrix, GeoProjections, LatLng}

object Pair {
  def of[K, V](element0: K, element1: V): Pair[K, V] = Pair[K, V](element0, element1)
  implicit def Tuple2Pair(a: (Int, Int)): Pair[Integer, Integer] = Pair(a._1, a._2)
  implicit def Pair2Tuple(a: Pair[Integer, Integer]): (Int, Int) = (a.first, a.second)
}

case class Pair[K, V](first: K, second: V)
class TransformSphericalCoordinates(sphericalLocation: LatLng){
  val location: Pair[Integer, Integer] = GeoProjections.MercatorProjection(sphericalLocation)
  val geoid: String = sphericalLocation.toString
}
case class TimeWindow(startInMinutes: Int, lastInMinutes: Int){
  val endInMinutes = startInMinutes + lastInMinutes
}
case class Depot(sphericalLocation: LatLng) extends TransformSphericalCoordinates(sphericalLocation)
object Order{
  val zero = Order(null, 0, null, 0)
}
case class Order(sphericalLocation: LatLng, demand: Int, timeWindow: TimeWindow, penalty: Int) extends TransformSphericalCoordinates(sphericalLocation)
case class Fleet(size: Int, carryingCapacity: Int, maxTimeOnTheRoadInMin: Int = Int.MaxValue){
  val costCoefficient: Int = 1 // minutes/meter
}
case class CVRP(depot: Depot, orders: Seq[Order], fleet: Fleet, distanceMatrix: DistanceMatrix){
  val dropLocationsLength = orders.map(_.location).length
  //val distinctLocations = (dropLocations :+ startLocation).distinct
  val routeLocationsInSpherical = orders.map(_.sphericalLocation) ++ Array.fill(fleet.size*2)(depot.sphericalLocation)
  val routeLocations = orders.map(_.location) ++ Array.fill(fleet.size*2)(depot.location)
  val vehicleStartIndices = fillIndices(dropLocationsLength, fleet.size)
  val vehicleEndIndices = fillIndices(dropLocationsLength + fleet.size, fleet.size)

  def fillIndices(start: Int, count: Int): Array[Int] = {
    (start until (start+count)).toArray
  }
}