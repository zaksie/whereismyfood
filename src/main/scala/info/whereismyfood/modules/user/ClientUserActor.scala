package info.whereismyfood.modules.user

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.cluster.pubsub.DistributedPubSub
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.modules.geo.Geolocation
import info.whereismyfood.modules.order.ProcessedOrder
import info.whereismyfood.modules.user.ClientUserActor.{ClientUpdates, OrderEnroute}
import info.whereismyfood.modules.user.CourierUserActor.{SubscribeToCourier, UnsubscribeToCourier}
import info.whereismyfood.modules.user.UserActorUtils._

import scala.collection.mutable
/**
  * Created by zakgoichman on 11/7/16.
  */
case class ClientSubscriptions(override val actor: ActorRef)
                              (implicit override val user: ClientUser, implicit override val mediator: ActorRef)
  extends Subscriptions(actor){
  override def selfTopic: String = Topics.clientUpdates + user.phone
}

object ClientUserActor extends HasPropsFunc[ClientUser] {
  import info.whereismyfood.modules.comm.JsonProtocol.WithType
  import info.whereismyfood.aux.MyConfig.OpCodes
  import spray.json._
  case class ClientUpdates(topicToFollow: String)
  case class OrderEnroute(orders: ProcessedOrder*){
    def toJsonString: String = {
      import info.whereismyfood.modules.order.ProcessedOrderJsonSupport._
      orders.toJson.compactPrint withOpCode OpCodes.Client.enroute
    }
  }
  case class ReportCourierPosition(loc: Geolocation){
    def toJsonString: String = {
      import info.whereismyfood.modules.geo.GeolocationJsonSupport._
      loc.toJson.compactPrint withOpCode OpCodes.Client.courierPosition(loc.key)
    }
  }

  def props(implicit user: ClientUser) =
    Props(new ClientUserActor)
}




class ClientUserActor(implicit user: ClientUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator
  var connectedUser:Option[ActorRef] = None
  val subscriptions = ClientSubscriptions(self)
  val orders = mutable.ArrayBuffer[ProcessedOrder]()
  var counter = 0

  import ClientUserActor._

  def receive = {
    case order: ProcessedOrder =>
      orders += order
      followOrder(order)
    case x: ReportCourierPosition =>
      println(s"""Counter: $counter
      Phone: ${user.phone}
      Location: $x
      """)
      counter += 1
      getUserOrDie ! OutgoingMessage(x.toJsonString)
    case ClientUpdates(topicToFollow: String) =>
      //TODO: Need to figure out the use for this yet
    case x: OrderEnroute =>
      getUserOrDie ! OutgoingMessage(x.toJsonString)
    case SubscribeToCourier(topic) =>
      subscriptions += topic
    case UnsubscribeToCourier(topic) =>
      subscriptions -= topic
    case Connected(outgoing) =>
      connectedUser = Some(outgoing)
      log.info("connected");
    case IncomingMessage(text) =>
      import Ops._
      parse(text) match {
        case x if x.op == geolocation =>
          Geolocation.register(x.payload) match {
            case Some(loc) =>
              println("Received location from client...")
            case None =>
              log.warning("failed to parse geolocation")
          }
      }
    case x =>
      println(x)
  }

  def getUserOrDie(): ActorRef = {
    connectedUser match {
      case Some(connection) => connection
      case _ =>
        self ! PoisonPill
        ActorRef.noSender
    }
  }

  def followOrder(order: ProcessedOrder): Unit = {
    //TODO: sort this out
    //subscriptions += Topics.courierGeolocation + order.courier.phone
  }
}