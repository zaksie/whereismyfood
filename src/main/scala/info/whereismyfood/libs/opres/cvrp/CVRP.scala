package info.whereismyfood.libs.opres.cvrp

/**
  * Created by zakgoichman on 10/24/16.
  */

import com.google.maps.model.LatLng
import com.google.ortools.constraintsolver.{FirstSolutionStrategy, NodeEvaluator2, RoutingModel, RoutingSearchParameters}
import info.whereismyfood.libs.math.{Distance, DistanceMatrix, GeoProjections}
import org.slf4j.LoggerFactory

object Pair {
  def of[K, V](element0: K, element1: V): Pair[K, V] = Pair[K, V](element0, element1)
  implicit def Tuple2Pair(a: (Int, Int)): Pair[Integer, Integer] = Pair(a._1, a._2)
}

case class Pair[K, V](first: K, second: V)
class TransformSphericalCoordinates(sphericalLocation: LatLng){
  val location: Pair[Integer, Integer] = GeoProjections.MercatorProjection(sphericalLocation)
}
case class TimeWindow(startInMinutes: Int, lastInMinutes: Int){
  val endInMinutes = startInMinutes + lastInMinutes
}
case class Depot(sphericalLocation: LatLng) extends TransformSphericalCoordinates(sphericalLocation)
case class Order(sphericalLocation: LatLng, demand: Int, timeWindow: TimeWindow, penalty: Int) extends TransformSphericalCoordinates(sphericalLocation)
case class Fleet(size: Int, carryingCapacity: Int, maxTimeOnTheRoadInMin: Int = 45){
  val costCoefficient: Int = 1000 // meters / minute
}
case class CVRP(depot: Depot, orders: Seq[Order], fleet: Fleet, distanceMatrix: DistanceMatrix){
  val startLocation = depot.location
  val dropLocations = orders.map(_.location)
  val distinctLocations = (dropLocations :+ startLocation).distinct
  val vehicleStartIndices = Array.fill(fleet.size)(0)
  val vehicleEndIndices = vehicleStartIndices
  val totalOrderCount = orders.map(_.demand).sum
}

object CVRPSolver {
  private val logger = LoggerFactory.getLogger(CVRPSolver.getClass)
  System.loadLibrary("jniortools")

  def solve(cvrp: CVRP) {
    logger.info("Creating model with " + cvrp.orders.length + " orders and " +
      cvrp.fleet.size + " vehicles.")
    val model = new RoutingModel(cvrp.distinctLocations.length, cvrp.fleet.size, cvrp.vehicleStartIndices, cvrp.vehicleEndIndices)


    val bigNumber = 100000
    val distanceCallback = new NodeEvaluator2() {
      override def run(firstIndex: Int, secondIndex: Int): Long = {
        logger.info("in distanceCallback")
        try {
          cvrp.distanceMatrix.get(firstIndex, secondIndex).getOrElse(Distance.zero).distanceInMeters
        } catch {
          case e: Exception => logger.error("error in distanceCallback", e); 0
        }
      }
    }
    model.addDimension(distanceCallback, bigNumber, bigNumber, false, "time")

    val demandCallback = new NodeEvaluator2() {
      override def run(firstIndex: Int, secondIndex: Int): Long = {
        logger.info("in demandCallback")
        try {
          cvrp.orders.lift(firstIndex).getOrElse(Order(null,0,null,0)).demand
        } catch {
          case e: Exception => logger.error("error in distanceCallback", e); 0
        }
      }
    }
    model.addDimension(demandCallback, 0, cvrp.fleet.carryingCapacity, true, "capacity")

    for (vehicle <- 0 until cvrp.fleet.size) {
      val distanceCostCallback = new NodeEvaluator2() {
        override def run(firstIndex: Int, secondIndex: Int): Long = {
          logger.info("in demandCallback")
          try {
            cvrp.fleet.costCoefficient *
              cvrp.distanceMatrix.get(firstIndex, secondIndex).getOrElse(Distance.zero).distanceInMeters
          } catch {
            case e: Exception => logger.error("error in distanceCallback", e); 0
          }
        }
      }
      model.setVehicleCost(vehicle, distanceCostCallback)
      model.cumulVar(model.end(vehicle), "time").setMax(cvrp.fleet.maxTimeOnTheRoadInMin)
    }
    for (order <- cvrp.orders.indices) {
      model.cumulVar(order, "time").setRange(cvrp.orders(order).timeWindow.startInMinutes, cvrp.orders(order).timeWindow.endInMinutes)
      val orders = Array(order)
      model.addDisjunction(orders, cvrp.orders(order).penalty)
    }
    val parameters = RoutingSearchParameters.newBuilder().mergeFrom(RoutingModel.defaultSearchParameters())
      .setFirstSolutionStrategy(FirstSolutionStrategy.Value.ALL_UNPERFORMED)
      .build()
    logger.info("Search")
    val solution = model.solveWithParameters(parameters)
    println(solution)
  }
}
