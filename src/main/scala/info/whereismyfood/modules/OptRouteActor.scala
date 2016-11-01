package info.whereismyfood.modules

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.google.gson.GsonBuilder
import com.google.maps.model.LatLng
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.matching.Regex
import akka.pattern.ask

/**
  * Created by zakgoichman on 10/24/16.
  */
object OptRoute {
  def props = Props(new OptRoute)
}

class OptRoute extends Actor {
  implicit val resolveTimeout = Timeout(30 seconds)
  val gson = new GsonBuilder().setPrettyPrinting().create()
  val coordInputPattern = new Regex("""\((.*?)\)""")
  val system = ActorSystemContainer.getSystem
  val actorRef = Await.result(system.actorSelection("/user/google-distance-matrix-api").resolveOne(), resolveTimeout.duration)
  override def receive: Receive = {
    case params: DistanceMatrixRequestParams => {
      val points = getLatLngs(params.destinations)
      val start = getLatLngs(params.start)
      val all = start ++ points
      val result = Await.result(actorRef ? all, resolveTimeout.duration)
      val json = gson.toJson(result)
      sender ! json
    }
  }

  def getLatLngs(s: String): Seq[LatLng] = {
    coordInputPattern.findAllIn(s).matchData.map[LatLng] {
      m =>
        val s = m.group(1).split(",")
        new LatLng(s(0).toDouble, s(1).toDouble)
    }.toSeq
  }
}
