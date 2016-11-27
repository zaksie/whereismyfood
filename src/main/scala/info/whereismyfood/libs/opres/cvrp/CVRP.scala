package info.whereismyfood.libs.opres.cvrp

/**
  * Created by zakgoichman on 10/24/16.
  */

import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.database.Databases

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import info.whereismyfood.modules.geo.{Distance, DistanceMatrix, LatLng}
import info.whereismyfood.modules.order.ProcessedOrder

case class Fleet(size: Int, carryingCapacity: Int, maxTimeOnTheRoadInMin: Int = Int.MaxValue){
  def isEmpty: Boolean = size == 0

  val costCoefficient: Int = 1 // minutes/meter
}

case class CVRPParams(depot: LatLng, fleet: Fleet, private val __orders: Seq[ProcessedOrder]) {
  val orders = __orders.groupBy(_.geoid).map(_._2.head).toSeq
  def allLocations: Seq[LatLng] = depot +: orders.map(_.client.geoaddress.get.latLng)
  val distanceMatrix = CVRPParams.getDistanceMatrix(allLocations)
}

object CVRPParams {
  def getDistanceMatrix(locations: Seq[LatLng]): Option[DistanceMatrix] = {
    import ActorSystemContainer.Implicits._

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
      val googleDistanceApiActorRef = Await.result(system.actorSelection("/user/libs/google-distance-api").resolveOne(), 60 seconds)
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
}