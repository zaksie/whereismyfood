package info.whereismyfood.modules.business

import java.time.{ZoneOffset, ZonedDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import akka.pattern.ask
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import info.whereismyfood.aux.MyConfig.{ActorNames, Topics}
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import info.whereismyfood.libs.opres.cvrp.{CVRPParams, Fleet}
import info.whereismyfood.modules.geo.{DeliveryRoute, LatLng, _}
import info.whereismyfood.modules.order.ProcessedOrder
import info.whereismyfood.modules.order.ProcessedOrder.OrderStatuses
import info.whereismyfood.modules.user.CourierUser

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import collection.JavaConverters._
import spray.json._

/**
  * Created by zakgoichman on 11/16/16.
  */

// Incoming case classes/objects
case object OnOrderMarkChange
case object PeriodicDispatchAttempt

// Outgoing case classes/objects
case class ReadyToShipOrders(businessId: Long, orders: Seq[ProcessedOrder]){
  import info.whereismyfood.modules.order.OrderJsonFormatters.{OpType_JsonFormatter, OpType_Orders_Json}

  def save() = {
    ProcessedOrder.save(orders:_*)
  }

  private val jsonRepresentation = OpType_Orders_Json("ship", orders)
  def toJsonString: String = {
    import OpType_JsonFormatter._
    jsonRepresentation.toJson.compactPrint
  }
}

object BusinessSingleton{
  private val prefix = "BusinessSingletonActor-"
  def getName(id: Long): String = prefix + id
  def getPath(id: Long): String = ActorNames.Paths.businessManager + "/singleton/" + getName(id)

  def props(business: Business) = Props(new BusinessSingleton(business))
  implicit val system = ActorSystemContainer.getSystem
}

class BusinessSingleton(business: Business) extends Actor with ActorLogging {
  case class DispatchItinerary(solution: VehicleRoutingProblemSolution)

  case class ItineraryError(err: String)

  case object NoAvailableCouriers

  import BusinessSingleton._

  private val name = getName(business.id)
  private val origin = business.address.latLng
  //TODO: put these below to good use
  private var latestSolution: Any = _
  private var latestHash: Int = 0
  val mediator = DistributedPubSub(context.system).mediator

  system.scheduler.schedule(10 seconds, 3 minutes, self, PeriodicDispatchAttempt)
  implicit val timeout = Timeout(30 seconds)

  println("Path of businessSingleton: " + context.self.path)

  override def receive: Receive = {
    case PeriodicDispatchAttempt | OnOrderMarkChange =>
      attemptDispatchOrders()
  }

  def attemptDispatchOrders(): Unit = {
    tryConstructRoute() match {
      case DispatchItinerary(itinerary) =>
        dispatchOrder(itinerary)
      case ItineraryError(err) =>
        log.error(s"Error encountered in calculating itinerary: ${err}")
      case NoAvailableCouriers =>
        log.warning("No available couriers were found")
      case None =>
      case x =>
        log.warning("in attemptDispatchOrders and got this: " + x)
    }
  }

  def activeAndReadyOrders = ProcessedOrder.retrieveAllActive(business.id).filter(OrderStatuses.isReady)
  def availableCourierCount: Seq[CourierUser] = CourierUser.getAllNear(origin, business.owners)

  def tryConstructRoute(): Any =
    activeAndReadyOrders match {
      case Seq() => None
      case orders =>
        val orderSet = orders.toSet
        val earliestOrderEpochSecond: Long = orderSet.reduce((a, b) => if (a.timestamp < b.timestamp) a else b).timestamp
        val optrouteActorRef = Await.result[ActorRef](system.actorSelection("/user/modules/optroute").resolveOne(), 15 seconds)
        val (haves, havenots) = ProcessedOrder.groupByHaveAddress(orderSet)
        askForUserIntervention(havenots, optrouteActorRef)
        tryGetSolution(haves, optrouteActorRef, earliestOrderEpochSecond)
    }

  def tryGetSolution(orders: Set[ProcessedOrder], optrouteActorRef: ActorRef, earliestOrderEpochSecond: Long): Any = {
    Fleet(availableCourierCount.size, MyConfig.Vars.vehicle_capacity) match {
      case fleet if fleet.isEmpty => NoAvailableCouriers
      case fleet =>
        val cvrp = CVRPParams(origin, fleet, orders.toSeq)
        Await.result(optrouteActorRef ? cvrp, 2 minutes) match {
          case CVRPOptimalSolution(sol) =>
            DispatchItinerary(sol)
          case CVRPSuboptimalSolution(sol) =>
            val now = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond
            if (now - earliestOrderEpochSecond > business.config.orderTimeConstraints_sec)
              DispatchItinerary(sol)
            else
              None
          case CVRPError(err) =>
            ItineraryError(err)
        }
    }
  }


  def askForUserIntervention(orders: Set[ProcessedOrder], optrouteActorRef: ActorRef): Any = {
    //TODO: do this part last
    sender ! None
  }

  def dispatchOrder(solution: VehicleRoutingProblemSolution): Unit = {
    val couriers = availableCourierCount
    val orders = activeAndReadyOrders.filterNot(x => solution.getUnassignedJobs.asScala.exists(_.getId == x.geoid))
    solution.getRoutes.asScala.zip(couriers).foreach {
      case (route, vehicle) =>
        val batch = route.getTourActivities.getActivities.asScala.foldLeft((origin, Seq[ProcessedOrder]())) { (b, a) =>
          val id = a.getLocation.getId
          LatLng.fromGeoId(id) match {
            case Some(ll) =>
              val deliveryRoute = DeliveryRoute.of(b._1, ll)
              val jobs = b._2 ++ orders.filter(_.geoid == id)
                      .map(_.copy(courier = vehicle.toCourierJsonOption, route = deliveryRoute, status = OrderStatuses.ENROUTE))
              (ll, jobs)
            case _ =>
              log.error("couldn't parse geoid: " + id)
              return
          }
        }._2

        val shipment = ReadyToShipOrders(business.id, batch)
        mediator ! Publish(Topics.courierUpdates + vehicle.phone, shipment)
        mediator ! Publish(Topics.chefUpdates + business.id, shipment)
        shipment.save
      case _ =>
        log.error("unexpected error in dispatchOrder :(")
        return
    }
  }
}
