package info.whereismyfood.libs.opres.cvrp

import com.graphhopper.jsprit.core.algorithm.box.Jsprit
import com.graphhopper.jsprit.core.problem.job.Service
import com.graphhopper.jsprit.core.problem.vehicle.{VehicleImpl, VehicleTypeImpl}
import com.graphhopper.jsprit.core.problem.{Location, VehicleRoutingProblem}
import com.graphhopper.jsprit.core.reporting.SolutionPrinter
import com.graphhopper.jsprit.core.reporting.SolutionPrinter.Print
import com.graphhopper.jsprit.core.util.{Solutions, VehicleRoutingTransportCostsMatrix}
import info.whereismyfood.libs.math.{DistanceMatrix, LatLng}

import scala.collection.JavaConverters._

/**
  * Created by zakgoichman on 10/29/16.
  */


class JspritCVRP(orders: Seq[Order], fleet: Fleet, startPos: LatLng, distanceMatrix: DistanceMatrix) {
  private val services: Seq[Service] = buildOrders()
  private val vehicles: Seq[VehicleImpl] = buildFleet()
  private val routingCostMatrix = readDistances(distanceMatrix)

  private def buildFleet(): Seq[VehicleImpl] = {
    /*
 * get a vehicle type-builder and build a type with the typeId "vehicleType" and a capacity of 2
 * you are free to add an arbitrary number of capacity dimensions with .addCacpacityDimension(dimensionIndex,dimensionValue)
 */
    val vehicleType = VehicleTypeImpl.Builder.newInstance("vehicleType")
      .addCapacityDimension(0, fleet.carryingCapacity).build()

    /*
     * get a vehicle-builder and build a vehicle located at (10,10) with type "vehicleType"
     */
    for {i <- 1 to fleet.size}
      yield VehicleImpl.Builder
        .newInstance("vehicle")
        .setStartLocation(Location.newInstance(startPos.geoid))
        .setType(vehicleType).build()

  }

  private def buildOrders(): Seq[Service] = {
    orders.map { order =>
      Service.Builder.newInstance(order.geoid)
        .addSizeDimension(0, order.demand)
        .setLocation(Location.newInstance(order.geoid)).build
    }
  }

  def solve() = {
    /*
 * again define a builder to build the VehicleRoutingProblem
 */
    val vrpBuilder = VehicleRoutingProblem.Builder.newInstance()
    vrpBuilder.addAllVehicles(vehicles.asJavaCollection)
    vrpBuilder.addAllJobs(services.asJavaCollection)
    vrpBuilder.setRoutingCost(routingCostMatrix)
    /*
     * build the problem
     * by default, the problem is specified such that FleetSize is INFINITE, i.e. an infinite number of
     * the defined vehicles can be used to solve the problem
     * by default, transport costs are computed as Euclidean distances
     */
    val problem = vrpBuilder.build()

    /*
* get the algorithm out-of-the-box.
*/
    val algorithm = Jsprit.createAlgorithm(problem)

    /*
    * and search a solution which returns a collection of solutions (here only one solution is constructed)
    */
    val solutions = algorithm.searchSolutions()

    /*
     * use the static helper-method in the utility class Solutions to get the best solution (in terms of least costs)
     */
    val bestSolution = Solutions.bestOf(solutions)

    SolutionPrinter.print(problem, bestSolution, Print.VERBOSE)
    bestSolution
  }

  private def readDistances(distanceMatrix: DistanceMatrix): VehicleRoutingTransportCostsMatrix = {
    val matrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(false)
    for(d <- distanceMatrix.getAll) {
      matrixBuilder.addTransportDistance(d.from.geoid, d.to.geoid, d.distance_meter)
    }
    matrixBuilder.build
  }
}
