package info.whereismyfood.modules.business

import java.time.{ZoneOffset, ZonedDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import akka.pattern.ask
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.geo.DistanceMatrixRequestParams
import info.whereismyfood.models.business.Business
import info.whereismyfood.models.order.ProcessedOrder
import info.whereismyfood.modules.geo.{EmptySolution, OptimalSolution, SuboptimalSolution}

import scala.concurrent.Await
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by zakgoichman on 11/16/16.
  */

case object OnOrderMarkChange
case object PeriodicDispatchAttempt

object BusinessSingleton{
  private val prefix = "BusinessSingletonActor-"
  def getName(id: Long): String = prefix + id
  def props(business: Business) = Props(new BusinessSingleton(business))
  implicit val system = ActorSystemContainer.getSystem
}
class BusinessSingleton(business: Business) extends Actor with ActorLogging {
  import BusinessSingleton._
  private val name = getName(business.id)

  //TODO: put these below to good use
  private var latestSolution: Any = _
  private var latestHash: Int = 0

  system.scheduler.scheduleOnce(1 minute, self, PeriodicDispatchAttempt)
  implicit val timeout = Timeout(30 seconds)

  override def receive: Receive = {
    case PeriodicDispatchAttempt | OnOrderMarkChange =>
      tryConstructRoute() match {
        case (OptimalSolution(solution), _) =>
          dispatchOrder(solution)
        case (SuboptimalSolution(solution), earliestOrderEpochSecond: Long) =>
          val now = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond
          if(now - earliestOrderEpochSecond > business.config.orderTimeConstraints_sec) dispatchOrder(solution)
        case EmptySolution =>
          log.error("Empty solution")
        case None =>
        case x =>
          log.info("Something else in tryConstructRoute")
      }
    //if(ok)
    //  mediator ! Publish(Topics.clientUpdates + order.client.phone, order)

  }

  def tryConstructRoute(): Any = {
    ProcessedOrder.retrieveAllActive(business.id) match{
      case Seq() => None
      case orders =>
        val orderSet = orders.toSet
        val earliestOrderEpochSecond: Long = orderSet.reduce((a,b)=> if (a.timestamp < b.timestamp) a else b).timestamp
        system.actorSelection("/user/modules/optroute").resolveOne().onComplete{
          case Success(optrouteActorRef) =>
            orderSet.groupBy(_.client.geoaddress.isDefined).foreach{
              case (true, haves) =>
                (tryGetSolution(haves, optrouteActorRef), earliestOrderEpochSecond)
              case (false, havenots) =>
                (askForUserIntervention(havenots, optrouteActorRef), earliestOrderEpochSecond)
            }
          case Failure(e) => log.error("Failed to find actor optroute", e)
        }
    }

    def tryGetSolution(orders: Set[ProcessedOrder], optrouteActorRef: ActorRef): Any = {
      val dmrp = DistanceMatrixRequestParams(business.address.latLng, orders.map(_.client.geoaddress.get.latLng))
      Await.result(optrouteActorRef ? dmrp, 1 minute)
    }

    def askForUserIntervention(orders: Set[ProcessedOrder], optrouteActorRef: ActorRef): Any = {
      //TODO: do this part last
      sender ! None
    }
  }

  def dispatchOrder(solution: VehicleRoutingProblemSolution): Unit ={

  }
}
