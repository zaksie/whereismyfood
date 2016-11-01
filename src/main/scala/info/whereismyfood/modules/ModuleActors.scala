package info.whereismyfood.modules

import akka.actor.Actor
import info.whereismyfood.libs.geo.GeoIndex

/**
  * Created by zakgoichman on 10/21/16.
  */
object Index extends Actor {
    context.actorOf(GeoIndex.props, "libs/geo/google-distance-matrix-api")
    context.actorOf(OpResIndex.props, "libs/opres/cvrp")
    context.actorOf(OptRouteActor.props, "modules/optroute")
}
