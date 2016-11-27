package info.whereismyfood.libs

import akka.actor.Actor
import info.whereismyfood.libs.geo.{DirectionsActor, DistanceMatrixActor, GeocodingActor}

/**
  * Created by zakgoichman on 10/21/16.
  */
class LibActors extends Actor {
    context.actorOf(DistanceMatrixActor.props, "google-distance-api")
    context.actorOf(GeocodingActor.props, "google-geocoding-api")
    context.actorOf(DirectionsActor.props, "google-directions-api")

    override def receive: Receive = {
        case _ => throw new Exception("Invalid actor call")
    }
}
