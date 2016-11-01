package info.whereismyfood.modules

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.google.gson.GsonBuilder
import com.google.maps.model.LatLng
import info.whereismyfood.aux.ActorSystemContainer

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.matching.Regex
import akka.pattern.ask
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams
import info.whereismyfood.libs.math.{Distance, DistanceMatrix, Location}
import info.whereismyfood.libs.opres.cvrp._

/**
  * Created by zakgoichman on 10/24/16.
  */
object OptRouteActor {
  def props = Props[OptRouteActor]
}

class OptRouteActor extends Actor {
  implicit val resolveTimeout = Timeout(300 seconds)
  val gson = new GsonBuilder().setPrettyPrinting().create()
  val coordInputPattern = new Regex("""\((.*?)\)""")
  val system = ActorSystemContainer.getSystem
  val googleDistanceApiActorRef = Await.result(system.actorSelection("/user/libs/google-distance-matrix-api").resolveOne(), resolveTimeout.duration)

  override def receive: Receive = {
    case params: DistanceMatrixRequestParams => {
      val locations = getLocations(params);
      val distanceMatrix = getDistanceMatrix(locations)
      val result = solveCVRP(locations, distanceMatrix.get)
      //val json = gson.toJson(result)
      sender ! result
    }
  }
  def getLocations(params : DistanceMatrixRequestParams) : Seq[LatLng] = {
    val points = getLatLngs(params.destinations)
    val start = getLatLngs(params.start)
    start ++ points
  }

  def getDistanceMatrix(locations: Seq[LatLng]): Option[DistanceMatrix] = {
    def getFromMemory(locations: Seq[LatLng]) : (Seq[LatLng], DistanceMatrix) = {
      val distanceMatrix = new DistanceMatrix()
      val distances = Await.result(Databases.inmemory.retrieve[Distance](locations.map(_.toString):_*), 30 seconds)
      val missing = locations.filter(y=>{
        !distances.exists(d=>d.from.latLng.toString == y.toString || d.to.latLng.toString == y.toString)
      })
      (missing, distanceMatrix.add(distances:_*))
    }

    def getFromDatabase(locations: Seq[LatLng]) : (Seq[LatLng], DistanceMatrix) = {
      val distanceMatrix = new DistanceMatrix()
      /*Databases.persistent.executeQuery("SELECT * FROM Distances").forEachRemaining(e => {
        val entity = e.asInstanceOf[Entity]
        val distance = Distance(Location(entity.getLatLng("from")
      })
      while(result.hasNext){
        result.next()
      }
      */
      (locations, distanceMatrix)
    }

    def getFromGoogleApi(locations: Seq[LatLng]) : Option[DistanceMatrix] = {
      val dm = Await.result(googleDistanceApiActorRef ? locations, resolveTimeout.duration).asInstanceOf[DistanceMatrix]
      Some(dm)
    }

    def updateMemory(distances: Seq[Distance]): Unit = {
      Databases.inmemory.save(distances)
    }

    def updateDatabase(distances: Seq[Distance]): Unit = {
      Databases.persistent.save[Distance](distances:_*)
    }

    getFromMemory(locations) match {
      case (Seq(), distanceMatrix: DistanceMatrix) => Some(distanceMatrix)
      case (missing: Seq[LatLng], distanceMatrix: DistanceMatrix) => getFromDatabase(missing) match {
        case (Seq(), dm2: DistanceMatrix) => {
          updateMemory(dm2.getAll)

          Some(distanceMatrix.merge(dm2))
        }
        case (missing2: Seq[LatLng], dm2: DistanceMatrix) => getFromGoogleApi(missing2) match {
          case Some(dm3) => {
            updateMemory(dm3.getAll ++ dm2.getAll)
            updateDatabase(dm3.getAll)

            Some(dm3.merge(dm2).merge(distanceMatrix))
          }
          case None => None
        }
      }
    }
  }

  def getLatLngs(s: String): Seq[LatLng] = {
    coordInputPattern.findAllIn(s).matchData.map[LatLng] {
      m =>
        val s = m.group(1).split(",")
        new LatLng(s(0).toDouble, s(1).toDouble)
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
