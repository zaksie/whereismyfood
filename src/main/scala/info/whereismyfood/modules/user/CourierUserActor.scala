package info.whereismyfood.modules.user

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.modules.business.ReadyToShipOrders
import info.whereismyfood.modules.geo.Geolocation
import info.whereismyfood.modules.user.ClientUserActor.OrderEnroute
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
  case class SubscribeToCourier(topic: String)
  case class UnsubscribeToCourier(topic: String)

  def props(implicit user: CourierUser) =
    Props(new CourierUserActor)
}

class CourierUserActor(implicit user: CourierUser) extends Actor with ActorLogging {
  implicit val mediator = DistributedPubSub(context.system).mediator
  implicit val id: Topics.ID = user.phone
  var connectedUser:Option[ActorRef] = None
  val subscriptions = CourierSubscriptions(self)

  def receive = {
    case Connected(outgoing) =>
      connectedUser = Some(outgoing)
      log.info("connected")
      mediator ! Publish(Topics.courierIsOnline, user)
    case PoisonPill =>
      //TODO: check if works and implement for the rest of the user actors
      connectedUser = None
      mediator ! Publish(Topics.courierIsOffline, user)
    case shipment: ReadyToShipOrders =>
      shipment.orders.foreach{
        order =>
          mediator ! Publish(Topics.clientUpdates(order.client.phone), OrderEnroute(order))
      }
    case IncomingMessage(text) =>
      import Ops._
      parse(text) match {
        case x if x.op == geolocation =>
          Geolocation.register(x.payload) match {
            case Some(loc) =>
              println("SENDING LOCATION...")
              mediator ! Publish(Topics.courierGeolocation + user.phone, loc)
              //mediator ! Publish(Topics.courierGeolocation, loc)
            case None =>
              log.warning("failed to parse geolocation")
          }
      }
    case x =>
      println(x)
  }
}