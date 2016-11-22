package info.whereismyfood.libs.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.geo.BrowserGeolocation
import info.whereismyfood.libs.user.UserActorUtils._
import info.whereismyfood.models.user.{CourierUser, Creds}

/**
  * Created by zakgoichman on 11/7/16.
  */
case class CourierSubscriptions(override val actor: ActorRef)
                               (implicit override val user: CourierUser, implicit override val mediator: ActorRef)
  extends Subscriptions(actor){
  override def selfTopic: String = Topics.courierUpdates + user.phone
}

object CourierUserActor {
  def props(implicit user: CourierUser) =
    Props(new CourierUserActor)
}

class CourierUserActor(implicit user: CourierUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator

  var connectedUser:Option[ActorRef] = None
  val subscriptions = CourierSubscriptions(self)

  def receive = {
    case Connected(outgoing) =>
      connectedUser = Some(outgoing)
      log.info("connected");
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