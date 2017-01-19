package info.whereismyfood.modules

import akka.actor.Actor
import akka.routing.RoundRobinPool
import info.whereismyfood.modules.auth.VerifyPhoneModule
import info.whereismyfood.modules.business.BusinessModule
import info.whereismyfood.modules.geo.OptRouteModule
import info.whereismyfood.modules.menu.{DishModule, MenuModule}
import info.whereismyfood.modules.order.OrderModule
import info.whereismyfood.modules.user.{AdminUserAssets, ChefModule, CourierModule, ManagerModule}

/**
  * Created by zakgoichman on 10/21/16.
  */
class ModuleActors extends Actor {
  val size = 2
  val big = 5
  context.actorOf(OptRouteModule.props.withRouter(RoundRobinPool(size)), name = "optroute")
  context.actorOf(VerifyPhoneModule.props.withRouter(RoundRobinPool(size)), "request-verify-phone")
  context.actorOf(OrderModule.props.withRouter(RoundRobinPool(size)), "order")
  context.actorOf(BusinessModule.props.withRouter(RoundRobinPool(size)), "business")
  context.actorOf(CourierModule.props.withRouter(RoundRobinPool(size)), "couriers")
  context.actorOf(ManagerModule.props.withRouter(RoundRobinPool(size)), "managers")
  context.actorOf(ChefModule.props.withRouter(RoundRobinPool(size)), "chefs")
  context.actorOf(DishModule.props.withRouter(RoundRobinPool(size)), "dishes")
  context.actorOf(MenuModule.props.withRouter(RoundRobinPool(size)), "menus")
  context.actorOf(AdminUserAssets.props.withRouter(RoundRobinPool(big)), "admin")

  override def receive: Receive = {
    case _ => throw new Exception("Invalid actor call")
  }
}
