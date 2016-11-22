package info.whereismyfood.modules.geo

import akka.actor.{Actor, Props}
import akka.pattern.ask
import com.google.gson.GsonBuilder
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams
import info.whereismyfood.libs.math.{Distance, DistanceMatrix, LatLng}
import info.whereismyfood.libs.opres.cvrp._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/24/16.
  */
case class OptimalSolution(solution: VehicleRoutingProblemSolution)
case class SuboptimalSolution(solution: VehicleRoutingProblemSolution)
case object EmptySolution

object OptRouteModule {
  def props = Props[OptRouteActor]
}

class OptRouteActor extends Actor {
  import ActorSystemContainer.Implicits._

  override def receive: Receive = {
    case params: DistanceMatrixRequestParams =>
      val locations = params.getLocations
      val distanceMatrix = getDistanceMatrix(locations)
      val result: VehicleRoutingProblemSolution = solveCVRP(params.start, locations, distanceMatrix.get)
      sender ! SuboptimalSolution(result)
  }

  def getDistanceMatrix(locations: Seq[LatLng]): Option[DistanceMatrix] = {
    def getMissing(locations: Seq[LatLng], distanceMatrix: DistanceMatrix) : Seq[LatLng] = {
      locations.filter(p=>{
        !distanceMatrix.getAll.exists(d=>d.from == p || d.to == p)
      })
    }

    def getFromMemory(locations: Seq[LatLng]) : (Seq[LatLng], DistanceMatrix) = {
      val distanceMatrix = new DistanceMatrix()
      val hashes = locations.flatMap{ from =>
        locations.flatMap{
          case to if from != to => Some(Distance.getHash(from, to))
          case _ => None
        }
      }
      val distances = Await.result(Databases.inmemory.retrieve[Distance](hashes:_*), 30 seconds)
      distanceMatrix.add(distances:_*)
      (getMissing(locations, distanceMatrix), distanceMatrix)
    }

    def getFromDatabase(locations: Seq[LatLng]) : (Seq[LatLng], DistanceMatrix) = {
      DistanceMatrix.getFromDBWithinRadius(locations) match {
        case Some(dm) =>
          (getMissing(locations, dm), dm)
        case None =>
          (locations, DistanceMatrix())
      }
    }

    def getFromGoogleApi(locations: Seq[LatLng]) : Option[DistanceMatrix] = {
      val googleDistanceApiActorRef = Await.result(context.system.actorSelection("/user/libs/google-distance-matrix-api").resolveOne(), 60 seconds)
      val dm = Await.result(googleDistanceApiActorRef ? locations, resolveTimeout.duration).asInstanceOf[DistanceMatrix]
      Some(dm)
    }

    def updateMemory(distances: Seq[Distance]): Unit = {
      Databases.inmemory.saveSeq(180 days, distances)
    }

    def updateDatabase(dm: DistanceMatrix): Unit = {
      dm.saveToDB()
    }

    getFromMemory(locations) match {
      case (Seq(), distanceMatrix: DistanceMatrix) => Some(distanceMatrix)
      case (missing: Seq[LatLng], distanceMatrix: DistanceMatrix) => getFromDatabase(missing) match {
        case (Seq(), dm2: DistanceMatrix) =>
          updateMemory(dm2.getAll)

          Some(distanceMatrix.merge(dm2))
        case (_: Seq[LatLng], dm2: DistanceMatrix) => getFromGoogleApi(locations) match {
          case Some(dm3) =>
            updateMemory(dm3.getAll ++ dm2.getAll)
            updateDatabase(dm3)

            Some(dm3.merge(dm2).merge(distanceMatrix))
          case None => None
        }
      }
    }
  }


  def solveCVRP(depotPoint: LatLng, allPoints: Seq[LatLng], distanceMatrix: DistanceMatrix): VehicleRoutingProblemSolution = {
    val orders = allPoints.tail.map(ll => Order(ll, 1, TimeWindow(0, 60), 30))
    //val depot = Depot(depotPoint)
    val fleet = Fleet(1, 2)

    //CVRP(depot, orders, fleet, distanceMatrix)
    new JspritCVRP(orders, fleet, depotPoint, distanceMatrix).solve()
  }
}
