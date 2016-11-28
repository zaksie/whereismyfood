package info.whereismyfood.modules.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.modules.business.ReadyToShipOrders
import info.whereismyfood.modules.order.ProcessedOrder
import info.whereismyfood.modules.user.UserActorUtils._
import spray.json._

/**
  * Created by zakgoichman on 11/7/16.
  */

import info.whereismyfood.modules.order.OrderJsonFormatters._
trait OpProcessedOrders

case class AddProcessedOrders(orders: Seq[ProcessedOrder]) extends OpProcessedOrders{
  private val jsonRepresentation = OpType_Orders_Json("add", orders)
  def toJsonString: String = {
    import OpType_JsonFormatter._
    jsonRepresentation.toJson.compactPrint
  }
}

case class ModifyProcessedOrders(orders: Seq[ProcessedOrder]) extends OpProcessedOrders {
  private val jsonRepresentation = OpType_Orders_Json("modify", orders)
  def toJsonString: String = {
    import OpType_JsonFormatter._
    jsonRepresentation.toJson.compactPrint
  }
}

case class DeleteProcessedOrder(orderId: String){
  private val jsonRepresentation = SingleOpType_Json("delete", orderId)
  def toJsonString: String = {
    import OpType_JsonFormatter._
    jsonRepresentation.toJson.compactPrint
  }
}

object ChefUserActor extends HasPropsFunc[ChefUser] {
  case class ChefSubscriptions(override val actor: ActorRef)
                              (implicit override val user: ChefUser, implicit override val mediator: ActorRef)
      extends Subscriptions(actor){
    override def selfTopic: String = {
      val s = Topics.chefUpdates + user.businessIds.head
      s
    }
  }
  def props(implicit user: ChefUser) =
    Props(new ChefUserActor)
}

class ChefUserActor(implicit user: ChefUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator

  var connectedUser: ActorRef = ActorRef.noSender
  val subscriptions = ChefUserActor.ChefSubscriptions(self)

  def receive = {
    case Connected(outgoing) =>
      log.info("chef ws actor connected");
      connectedUser = outgoing
    case x: AddProcessedOrders =>
      log.info("new orders arrived");
      connectedUser ! OutgoingMessage(x.toJsonString)
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