package info.whereismyfood.libs.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator._
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.auth.Creds
import info.whereismyfood.libs.geo.{BrowserGeolocation}
import info.whereismyfood.libs.user.GenericUserActor._

import scala.collection.mutable
/**
  * Created by zakgoichman on 11/7/16.
  */
object ClientUserActor {
  def props(implicit creds: Creds) =
    Props(new ClientUserActor)
}

class ClientUserActor(implicit creds: Creds) extends Actor with ActorLogging {

  implicit val mediator = DistributedPubSub(context.system).mediator
  var user:Option[ActorRef] = None
  val subscriptions: mutable.Set[String]  = mutable.Set()
  mediator ! Subscribe(Topics.clientUpdates + creds.dbid, self)
  updateSubscriptions

  def receive = {
    case SubscribeToCourier(topic) =>
      subscriptions += topic
    case UnsubscribeToCourier(topic) =>
      subscriptions -= topic
    case courierLocation: (Creds, BrowserGeolocation) =>
      println(courierLocation)
    case SubscribeAck(Subscribe(Topics.courierGeolocation, None, `self`)) ⇒
      log.info("subscribing");
    case Connected(outgoing) =>
      user = Some(outgoing)
      log.info("connected");
    case CurrentTopics(topics) =>
      val newSubscriptions = subscriptions -- topics
      val unsubscribe = topics -- subscriptions
      newSubscriptions.foreach(mediator ! Subscribe(_, self))
      unsubscribe.foreach(mediator ! Unsubscribe(_, self))

    case IncomingMessage(text) =>
      log.warning("client not supposed to be sending anything to server")
    case x =>
      println(x)
  }

  def updateSubscriptions(implicit mediator: ActorRef): Unit ={
    mediator ! GetTopics
  }
}