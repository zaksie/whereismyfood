package info.whereismyfood.modules.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.modules.user.HasPropsFunc
import info.whereismyfood.modules.geo.Geolocation
import info.whereismyfood.modules.user.UserActorUtils._

/**
  * Created by zakgoichman on 11/7/16.
  */
case class CourierSubscriptions(override val actor: ActorRef)
                               (implicit override val user: CourierUser, implicit override val mediator: ActorRef)
  extends Subscriptions(actor){
  override def selfTopic: String = Topics.courierUpdates + user.phone
}

object CourierUserActor extends HasPropsFunc[CourierUser] {
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
      Geolocation.register(text) match {
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