package info.whereismyfood.modules

import akka.actor.Actor
import akka.routing.RoundRobinPool
import info.whereismyfood.modules.auth.VerifyPhoneModule
import info.whereismyfood.modules.courier.CourierModule
import info.whereismyfood.modules.geo.OptRouteModule
import info.whereismyfood.modules.order.OrderModule

/**
  * Created by zakgoichman on 10/21/16.
  */
class ModuleActors extends Actor {
    context.actorOf(OptRouteModule.props.withRouter(RoundRobinPool(5)), name = "optroute")
    context.actorOf(VerifyPhoneModule.props.withRouter(RoundRobinPool(5)), "request-verify-phone")
    context.actorOf(OrderModule.props.withRouter(RoundRobinPool(5)), "order")
    context.actorOf(CourierModule.props.withRouter(RoundRobinPool(5)), "courier-action")

    override def receive: Receive = {
        case _ => throw new Exception("Invalid actor call")
    }
}
