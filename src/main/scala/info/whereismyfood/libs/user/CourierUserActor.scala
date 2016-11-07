package info.whereismyfood.libs.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.auth.Creds
import info.whereismyfood.libs.geo.{BrowserGeolocation, GeolocationRegistry}
import info.whereismyfood.libs.user.GenericUserActor._

/**
  * Created by zakgoichman on 11/7/16.
  */
object CourierUserActor {
  def props(implicit creds: Creds) =
    Props(new CourierUserActor)
}

class CourierUserActor(implicit creds: Creds) extends Actor with ActorLogging {
  val mediator = DistributedPubSub(context.system).mediator

  var user:Option[ActorRef] = None

  def receive = {
    case SubscribeAck(Subscribe(Topics.courierGeolocation, None, `self`)) ⇒
      log.info("subscribing");
    case Connected(outgoing) =>
      user = Some(outgoing)
      log.info("connected");
    case IncomingMessage(text) =>
      GeolocationRegistry.register(text) match {
        case Some(loc) =>
          mediator ! Publish(Topics.courierGeolocation, loc)
        case None =>
          log.warning("failed to parse geolocation")
      }
    case x =>
      println(x)
  }
}