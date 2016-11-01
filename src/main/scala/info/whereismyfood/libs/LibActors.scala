package info.whereismyfood.libs

import akka.actor.Actor

/**
  * Created by zakgoichman on 10/21/16.
  */
object LibsActors extends Actor {
    context.actorOf(GeoIndex.props, "libs/geo/google-distance-matrix-api")
    context.actorOf(OpResIndex.props, "libs/opres/cvrp")
    context.actorOf(OptRouteActor.props, "modules/optroute")
}
