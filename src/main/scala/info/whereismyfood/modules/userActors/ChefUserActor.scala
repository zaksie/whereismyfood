package info.whereismyfood.modules.userActors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.geo.BrowserGeolocation
import info.whereismyfood.models.order.ProcessedOrder
import info.whereismyfood.models.user.{ChefUser, HasPropsFunc}
import info.whereismyfood.modules.userActors.UserActorUtils._
import spray.json._

/**
  * Created by zakgoichman on 11/7/16.
  */
private case class OpType_Orders_Json(`type`: String, orders: Seq[ProcessedOrder])
private case class SingleOpType_Json(`type`: String, orderId: String)
private object OpType_JsonFormatter extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.models.order.ProcessedOrderJsonSupport._
  implicit val opType_Orders_JsonFormatter = jsonFormat(OpType_Orders_Json, "type", "orders")
  implicit val singleOpType_JsonFormatter = jsonFormat(SingleOpType_Json, "type", "orderId")
}

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

case class ChefSubscriptions(override val actor: ActorRef)
                               (implicit override val user: ChefUser, implicit override val mediator: ActorRef)
  extends Subscriptions(actor){
  override def selfTopic: String =
    Topics.chefUpdates + user.businessIds.head
}

object ChefUserActor extends HasPropsFunc[ChefUser] {
  def props(implicit user: ChefUser) =
    Props(new ChefUserActor)
}

class ChefUserActor(implicit user: ChefUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator

  var connectedUser: ActorRef = ActorRef.noSender
  val subscriptions = ChefSubscriptions(self)

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

    case IncomingMessage(text) =>
      BrowserGeolocation.register(text) match {
        case Some(loc) =>
          println("SENDING LOCATION...")
          mediator ! Publish(Topics.courierGeolocation + user.phone, loc)
        case None =>
          log.warning("failed to parse geolocation")
      }
    case x =>
      println(x)
  }
}