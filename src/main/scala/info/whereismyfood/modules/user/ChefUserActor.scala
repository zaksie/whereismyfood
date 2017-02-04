package info.whereismyfood.modules.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import info.whereismyfood.aux.MyConfig.{OpCodes, Topics}
import info.whereismyfood.modules.business.ReadyToShipOrders
import info.whereismyfood.modules.order.ProcessedOrder
import info.whereismyfood.modules.user.UserActorUtils._
import spray.json._
import info.whereismyfood.modules.comm.JsonProtocol.WithType
import info.whereismyfood.modules.order.ProcessedOrderJsonSupport._
import akka.pattern.ask
import akka.util.Timeout
import concurrent.duration._
/**
  * Created by zakgoichman on 11/7/16.
  */

trait OpProcessedOrders

case class AddProcessedOrders(orders: Seq[ProcessedOrder]) extends OpProcessedOrders{
  def toJsonString: String = {
    orders.toJson.compactPrint withOpCode OpCodes.Chef.add
  }
}

case class ModifyProcessedOrders(orders: Seq[ProcessedOrder]) extends OpProcessedOrders {
  def toJsonString: String = {
    orders.toJson.compactPrint withOpCode OpCodes.Chef.modify
  }
}

case class DeleteProcessedOrder(orderId: String){
  def toJsonString: String = {
    orderId.toJson.compactPrint withOpCode OpCodes.Chef.delete
  }
}

object ChefUserActor extends HasPropsFunc[ChefUser] {
  def props(implicit user: ChefUser) =
    Props(new ChefUserActor)
}

class ChefUserActor(implicit user: ChefUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator

  var connectedUser: ActorRef = ActorRef.noSender
  val chefSubscriptions = ChefSubscriptions(self)

  def receive = {
    case Connected(outgoing) =>
      log.info("chef ws actor connected");
      connectedUser = outgoing
    case x: AddProcessedOrders =>
      log.info("new orders arrived");
      val out = x.toJsonString
      connectedUser ! OutgoingMessage(out)
    case x: ModifyProcessedOrders =>
      log.info("new orders arrived");
      connectedUser ! OutgoingMessage(x.toJsonString)
    case x: DeleteProcessedOrder =>
      log.info(s"order ${x.orderId} deleted");
      connectedUser ! OutgoingMessage(x.toJsonString)
    case x: ReadyToShipOrders =>
      connectedUser ! OutgoingMessage(x.toJsonString)
  }
}

case class ChefSubscriptions(override val actor: ActorRef)
                            (implicit override val user: ChefUser, implicit override val mediator: ActorRef)
    extends Subscriptions(actor){
  implicit val timeout = Timeout(20 seconds)
  override def selfTopic: String = {
    Topics.chefUpdates(user.businessIds.head)
  }

  val newOrdersTopic = Topics.newOrders()
  mediator ? Subscribe(newOrdersTopic, actor) map{
    case SubscribeAck(_) =>
      subscriptions += newOrdersTopic
  }
}