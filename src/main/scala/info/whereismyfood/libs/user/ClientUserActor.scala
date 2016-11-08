package info.whereismyfood.libs.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.CurrentTopics
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.auth.Creds
import info.whereismyfood.libs.geo.BrowserGeolocation
import info.whereismyfood.libs.order.Order
import info.whereismyfood.libs.user.UserActorUtils._
import scala.collection.mutable
/**
  * Created by zakgoichman on 11/7/16.
  */
case class ClientSubscriptions(override val actor: ActorRef)
                              (implicit override val creds: Creds, implicit override val mediator: ActorRef)
  extends Subscriptions(actor){
  override def selfTopic: String = Topics.clientUpdates + creds.phone
}

object ClientUserActor {
  def props(implicit creds: Creds) =
    Props(new ClientUserActor)
}

class ClientUserActor(implicit creds: Creds) extends Actor with ActorLogging {

  implicit val mediator = DistributedPubSub(context.system).mediator
  var user:Option[ActorRef] = None
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
      Phone: $creds
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
      user = Some(outgoing)
      log.info("connected");
    case IncomingMessage(text) =>
      log.warning("client not supposed to be sending anything to server")
    case x =>
      println(x)
  }

  def followOrder(order: Order): Unit = {
    subscriptions += Topics.courierGeolocation + order.courier.phone
  }
}