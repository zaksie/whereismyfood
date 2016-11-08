package info.whereismyfood.modules

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.gson.GsonBuilder
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams
import info.whereismyfood.libs.math.{Distance, DistanceMatrix, LatLng, Location}
import info.whereismyfood.libs.opres.cvrp._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.matching.Regex

/**
  * Created by zakgoichman on 10/24/16.
  */
object OptRouteModule {
  def props = Props[OptRouteActor]
}

class OptRouteActor extends Actor {
  implicit val resolveTimeout = Timeout(300 seconds)
  val gson = new GsonBuilder().setPrettyPrinting().create()
  val coordInputPattern = new Regex("""\((.*?)\)""")
  val system = ActorSystemContainer.getSystem
  val googleDistanceApiActorRef = Await.result(system.actorSelection("/user/libs/google-distance-matrix-api").resolveOne(), resolveTimeout.duration)

  override def receive: Receive = {
    case params: DistanceMatrixRequestParams =>
      val locations = getLocations(params)
      val distanceMatrix = getDistanceMatrix(locations)
      val result = solveCVRP(locations, distanceMatrix.get)
      //val json = gson.toJson(result)
      sender ! result
  }
  def getLocations(params : DistanceMatrixRequestParams) : Seq[LatLng] = {
    val points = getLatLngs(params.destinations)
    val start = getLatLngs(params.start)
    start ++ points
  }

  def getDistanceMatrix(locations: Seq[LatLng]): Option[DistanceMatrix] = {
    def getMissing(locations: Seq[LatLng], distanceMatrix: DistanceMatrix) : Seq[LatLng] = {
      locations.filter(p=>{
        !distanceMatrix.getAll.exists(d=>d.from.latLng == p || d.to.latLng == p)
      })
    }

    def getFromMemory(locations: Seq[LatLng]) : (Seq[LatLng], DistanceMatrix) = {
      val distanceMatrix = new DistanceMatrix()
      val hashes = locations.flatMap{ from =>
        locations.flatMap{ to=>
          if(from != to) Some(Distance.getHash(from, to).toString)
          else None
        }
      }
      val distances = Await.result(Databases.inmemory.retrieve[Distance](hashes:_*), 30 seconds)
      distanceMatrix.add(distances:_*)
      (getMissing(locations, distanceMatrix), distanceMatrix)
    }

    def getFromDatabase(locations: Seq[LatLng]) : (Seq[LatLng], DistanceMatrix) = {
      val distanceMatrix = DistanceMatrix.getFromDB(locations).getOrElse(DistanceMatrix())
      (getMissing(locations, distanceMatrix), distanceMatrix)
    }

    def getFromGoogleApi(locations: Seq[LatLng]) : Option[DistanceMatrix] = {
      val dm = Await.result(googleDistanceApiActorRef ? locations, resolveTimeout.duration).asInstanceOf[DistanceMatrix]
      Some(dm)
    }

    def updateMemory(distances: Seq[Distance]): Unit = {
      Databases.inmemory.saveSeq(distances)
    }

    def updateDatabase(distances: Seq[Distance]): Unit = {
      Databases.persistent.save[Distance](distances:_*)
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
            updateDatabase(dm3.getAll)

            Some(dm3.merge(dm2).merge(distanceMatrix))
          case None => None
        }
      }
    }
  }

  def getLatLngs(s: String): Seq[LatLng] = {
    coordInputPattern.findAllIn(s).matchData.map[LatLng] {
      m =>
        val s = m.group(1).split(",")
        LatLng(s(0).toDouble, s(1).toDouble)
    }.toSeq
  }

  def solveCVRP(points: Seq[LatLng], distanceMatrix: DistanceMatrix): VehicleRoutingProblemSolution = {
    val orders = points.tail.map(ll => Order(ll, 1, TimeWindow(0, 120), 30))
    val depot = Depot(points.head)
    val fleet = Fleet(1, 2)
    val depotLocation = Location("start", points.head)

    //CVRP(depot, orders, fleet, distanceMatrix)
    val cvrp = new JspritCVRP(orders, fleet, depotLocation, distanceMatrix)
    cvrp.solve()
  }
}
