package info.whereismyfood.modules.userActors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.geo.BrowserGeolocation
import info.whereismyfood.models.order.Order
import info.whereismyfood.models.user.{ClientUser, Creds, HasPropsFunc}
import info.whereismyfood.modules.userActors.UserActorUtils._

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
  def props(implicit user: ClientUser) =
    Props(new ClientUserActor)
}

class ClientUserActor(implicit user: ClientUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator
  var connectedUser:Option[ActorRef] = None
  val subscriptions = ClientSubscriptions(self)
  val orders = mutable.ArrayBuffer[Order]()
  var counter = 0

  def receive = {
    case s: String =>
      println(s)
    case order: Order =>
      orders += order
      followOrder(order)
    case courierLocation: BrowserGeolocation =>
      println(s"""Counter: $counter
      Phone: ${user.phone}
      Location: $courierLocation
      """)
      counter += 1
    case ClientUpdates(topicToFollow: String) =>
      println(topicToFollow)
    case SubscribeToCourier(topic) =>
      subscriptions += topic
    case UnsubscribeToCourier(topic) =>
      subscriptions -= topic
    case courierLocation: (Creds, BrowserGeolocation) =>
      println(courierLocation)
    case Connected(outgoing) =>
      connectedUser = Some(outgoing)
      log.info("connected");
    case IncomingMessage(text) =>
      log.warning("client not supposed to be sending anything to server")
    case x =>
      println(x)
  }

  def followOrder(order: Order): Unit = {
    //TODO: sort this out
    //subscriptions += Topics.courierGeolocation + order.courier.phone
  }
}