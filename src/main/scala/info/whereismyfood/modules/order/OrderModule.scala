package info.whereismyfood.modules.order

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.modules.business.{BusinessSingleton, OnOrderMarkChange}
import info.whereismyfood.modules.geo.GeoMySQLInterface
import info.whereismyfood.modules.menu.{Dish, DishToAdd}
import info.whereismyfood.modules.order.ProcessedOrder.OrderStatuses
import info.whereismyfood.modules.user._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/**
  * Created by zakgoichman on 10/24/16.
  */

object OrderModule {
  case class AddOrders(orders: Orders)
  case class DeleteOrders(businessId: Long, orderId: String)
  case class MarkOrdersReady(businessId: Long, orderId: String, mark: Boolean)
  case class GetOpenOrders(businessId: Long)
  case class GetEnrouteOrders(businessId: Long)
  case class GetOpenOrderForUser(creds: Creds)
  case class PutOrderItemForUser(creds: Creds, item: DishToAdd)
  case class DeleteOrderItemForUser(creds: Creds, itemId: String)

  def props = Props[OrderActor]

  val openOrderPrefix = "openOrderAwaitingRouting-"

  val singletonProxyMap = collection.mutable.HashMap[String, ActorRef]()
}

class OrderActor extends Actor with ActorLogging {
  implicit val timeout = 100
  val mediator = DistributedPubSub(context.system).mediator

  import OrderModule._


  override def receive: Receive = {
    case GetOpenOrders(businessId) =>
      log.info("Getting open orders...")
      sender ! ProcessedOrder.retrieveAllActive(businessId).filter(OrderStatuses.notYetShipped)
    case GetEnrouteOrders(businessId) =>
      log.info("Getting enroute orders...")
      sender ! ProcessedOrder.retrieveAllActive(businessId).filter(OrderStatuses.isEnroute)
    case AddOrders(orders) =>
      sender ! addOrders(orders)
    case x: DeleteOrders =>
      sender ! deleteOrder(x)
    case x: MarkOrdersReady =>
      sender ! markOrder(x)
    case x: GetOpenOrderForUser =>
      sender ! getOpenOrderForUser(x)
    case x: PutOrderItemForUser =>
      sender ! putOpenOrderForUser(x)
    case x: DeleteOrderItemForUser =>
      sender ! deleteOpenOrderForUser(x)

  }

  def getOpenOrderForUser(x: GetOpenOrderForUser): Option[OpenOrder] = {
    OpenOrder.retrieveBy(x.creds.phone)
  }

  def putOpenOrderForUser(x: PutOrderItemForUser): Option[OrderItem] = {
    Dish.find(x.item.dishId) match {
      case Some(dish) =>
        OrderItem.of(dish, x.item) match {
          case Some(orderItem) =>
            Future(OpenOrder.addItem(x.creds.phone, orderItem))
            Some(orderItem)
          case _ => None
        }
      case _ =>
        None
    }
  }

  def deleteOpenOrderForUser(x: DeleteOrderItemForUser): Boolean = {
    OpenOrder.removeItem(x.creds.phone, x.itemId)
  }

  def markOrder(x: MarkOrdersReady): Boolean = {
    // This is the only case that requires notifying route planner
    if(ProcessedOrder.mark(x.businessId, x.orderId, x.mark))
        notifyRoutePlanner(x.businessId)
    else false
  }

  def deleteOrder(req: DeleteOrders): Boolean = {
    ProcessedOrder.delete(req.businessId, req.orderId)
    mediator ! Publish(Topics.chefUpdates + req.businessId, DeleteProcessedOrder(req.orderId))
    true
  }

  def addOrders(orders: Orders): Seq[ProcessedOrder] =
      ProcessedOrder.allIdsUnique(orders) match {
        case false => Seq()
        case _ =>
          processOrder(orders, AddProcessedOrders)
      }

  def processOrder(orders: Orders, op: Seq[ProcessedOrder] => OpProcessedOrders): Seq[ProcessedOrder] = {
    try {
      implicit val businessId = orders.businessId
      val processedOrders = orders.orders map formatOrder
      saveLocation(processedOrders)
      saveProcessedOrders(processedOrders)
      mediator ! Publish(Topics.chefUpdates + orders.businessId, op(processedOrders))
      processedOrders
    } catch {
      case e: Exception =>
        log.error("Failed in processNewOrChangedOrders {}", e)
        Seq()
    }
  }

  def formatOrder(order: Order)(implicit businessId: Long): ProcessedOrder = {
    ProcessedOrder.of(order)
  }

  def saveProcessedOrders(orders: Seq[ProcessedOrder])(implicit businessId: Long): Boolean = {
    ProcessedOrder.save(orders: _*)
  }

  def saveLocation(orders:Seq[ProcessedOrder]): Unit = {
    orders.map{
      case order if order.client.geoaddress.isDefined =>
        GeoMySQLInterface.saveAddressAndLocation(order.client.geoaddress.get)
      case _ => //Do nothing and nothing to do
    }
  }

  def notifyRoutePlanner(businessId: Long): Boolean = {
    Try {
      val name = BusinessSingleton.getName(businessId)
      val path = BusinessSingleton.getPath(businessId)
      singletonProxyMap.get(name) match {
        case Some(aref) =>
          aref ! OnOrderMarkChange
        case _ =>
          val aref = context.actorOf(
            ClusterSingletonProxy.props(
              singletonManagerPath = path,
              settings = ClusterSingletonProxySettings(context.system)), name + "-proxy")

          singletonProxyMap.put(name, aref)
          aref ! OnOrderMarkChange
      }
    }.isSuccess
  }
}


