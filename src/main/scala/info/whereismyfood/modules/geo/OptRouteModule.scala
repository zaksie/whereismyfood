package info.whereismyfood.modules.geo

import akka.actor.{Actor, Props}
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import info.whereismyfood.libs.opres.cvrp._

import scala.util.Try


/**
  * Created by zakgoichman on 10/24/16.
  */
case class CVRPOptimalSolution(solution: VehicleRoutingProblemSolution)
case class CVRPSuboptimalSolution(solution: VehicleRoutingProblemSolution)
case class CVRPError(err: String)

object OptRouteModule {
  def props = Props[OptRouteActor]
}

class OptRouteActor extends Actor {

  override def receive: Receive = {
    case x: CVRPParams =>
      sender ! solveCVRP(x)
  }

  def isSuboptimal(params: CVRPParams): Boolean = params.fleet.carryingCapacity > params.orders.size

  def solveCVRP(params: CVRPParams): Any = {
    try{
      implicit val solution = new JspritCVRP(params).solve()
      isSuboptimal(params) match {
        case true => CVRPSuboptimalSolution(solution)
        case _ => CVRPOptimalSolution(solution)
      }
    }catch{
      case e: Exception => CVRPError(e.getMessage)
    }
  }
}
