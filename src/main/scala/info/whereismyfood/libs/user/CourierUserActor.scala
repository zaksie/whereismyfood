package info.whereismyfood.libs.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish}
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.auth.Creds
import info.whereismyfood.libs.geo.{GeolocationRegistry}
import info.whereismyfood.libs.user.UserActorUtils._

/**
  * Created by zakgoichman on 11/7/16.
  */
case class CourierSubscriptions(override val actor: ActorRef)
                               (implicit override val creds: Creds, implicit override val mediator: ActorRef)
  extends Subscriptions(actor){
  override def selfTopic: String = Topics.courierUpdates + creds.phone
}

object CourierUserActor {
  def props(implicit creds: Creds) =
    Props(new CourierUserActor)
}

class CourierUserActor(implicit creds: Creds) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator

  var user:Option[ActorRef] = None
  val subscriptions = CourierSubscriptions(self)

  def receive = {
    case Connected(outgoing) =>
      user = Some(outgoing)
      log.info("connected");
    case IncomingMessage(text) =>
      GeolocationRegistry.register(text) match {
        case Some(loc) =>
          println("SENDING LOCATION...")
          mediator ! Publish(Topics.courierGeolocation+creds.phone, loc)
        case None =>
          log.warning("failed to parse geolocation")
      }
    case x =>
      println(x)
  }
}