package info.whereismyfood.libs

import akka.actor.Actor
import info.whereismyfood.libs.geo.DistanceMatrixActor

/**
  * Created by zakgoichman on 10/21/16.
  */
class LibActors extends Actor {
    context.actorOf(DistanceMatrixActor.props, "google-distance-matrix-api")

    override def receive: Receive = {
        case _ => {
            throw new Exception("Invalid actor call")
        }
    }
}
