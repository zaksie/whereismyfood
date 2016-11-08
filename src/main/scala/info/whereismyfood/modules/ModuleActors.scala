package info.whereismyfood.modules

import akka.actor.Actor
import akka.routing.RoundRobinPool

/**
  * Created by zakgoichman on 10/21/16.
  */
class ModuleActors extends Actor {
    context.actorOf(OptRouteModule.props.withRouter(RoundRobinPool(5)), name = "optroute")
    context.actorOf(VerifyPhoneModule.props.withRouter(RoundRobinPool(5)), "request-verify-phone")
    context.actorOf(NewOrderModule.props, "new-order")

    override def receive: Receive = {
        case _ => throw new Exception("Invalid actor call")
    }
}
