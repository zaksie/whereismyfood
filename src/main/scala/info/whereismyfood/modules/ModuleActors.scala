package info.whereismyfood.modules

import akka.actor.Actor

/**
  * Created by zakgoichman on 10/21/16.
  */
class ModuleActors extends Actor {
    context.actorOf(OptRouteActor.props, "optroute")
    override def receive: Receive = {
        case _ => {
            throw new Exception("Invalid actor call")
        }
    }
}
