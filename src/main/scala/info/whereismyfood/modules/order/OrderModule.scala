package info.whereismyfood.modules.order

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.aux.MyConfig.ActorNames
import info.whereismyfood.libs.geo.GeoMySQLInterface
import info.whereismyfood.models.order.{Order, Orders, ProcessedOrder}
import info.whereismyfood.modules.business.{BusinessSingleton, OnOrderMarkChange}

import scala.concurrent.duration._
import scala.util.Try


/**
  * Created by zakgoichman on 10/24/16.
  */

object OrderModule {

  implicit val resolveTimeout = Timeout(60 seconds)
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  case class AddOrders(orders: Orders)
  case class ModifyOrders(orders: Orders)
  case class DeleteOrders(businessId: Long, orderId: String)
  case class MarkOrdersReady(businessId: Long, orderId: String, mark: Boolean)

  def props = Props[OrderActor]

  val openOrderPrefix = "openOrderAwaitingRouting-"

  val singletonProxyMap = collection.mutable.HashMap[String, ActorRef]()
}

class OrderActor extends Actor with ActorLogging {
  implicit val timeout = 100
  val mediator = DistributedPubSub(context.system).mediator

  import OrderModule._

  override def receive: Receive = {
    case AddOrders(orders) =>
      sender ! processNewOrChangedOrders(orders)
    case ModifyOrders(orders) =>
      sender ! processNewOrChangedOrders(orders)
    case DeleteOrders(businessId, orderId) =>
      ProcessedOrder.delete(businessId, orderId)
    case MarkOrdersReady(businessId, orderId, mark) =>
      // This is the only case that requires notifying route planner
      implicit val name = BusinessSingleton.getName(businessId)
      if(ProcessedOrder.mark(businessId, orderId, mark))
        notifyRoutePlanner
  }

  def processNewOrChangedOrders(orders: Orders): Boolean =
    try{
      implicit val businessId = orders.businessId
      val processedOrders = orders.orders map formatOrder
      saveLocation(processedOrders)
      saveProcessedOrders(processedOrders)
      true
    }catch{
      case e: Exception =>
        log.error("Failed in processNewOrChangedOrders {}", e)
        false
    }

  def formatOrder(order: Order)(implicit businessId: Long): ProcessedOrder = {
    ProcessedOrder.of(order)
  }

  def saveProcessedOrders(orders: Seq[ProcessedOrder])(implicit businessId: Long): Boolean = {
    ProcessedOrder.save(businessId, orders: _*)
  }

  def saveLocation(orders:Seq[ProcessedOrder]): Unit = {
    orders.map{
      case order if order.client.address.isDefined =>
        GeoMySQLInterface.saveAddressAndLocation(order.client.address.get)
      case _ => //Do nothing and nothing to do
    }
  }

  def notifyRoutePlanner(implicit name: String): Boolean = {
    Try {
      singletonProxyMap.get(name) match {
        case Some(aref) =>
          aref ! OnOrderMarkChange
        case _ =>
          val aref = context.actorOf(
            ClusterSingletonProxy.props(
              singletonManagerPath = ActorNames.Paths.businessManager + name,
              settings = ClusterSingletonProxySettings(context.system)),
            name = name + "-proxy")

          singletonProxyMap.put(name, aref)
          aref ! OnOrderMarkChange
      }
    }.isSuccess
  }
}


