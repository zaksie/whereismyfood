package info.whereismyfood.modules.courier

import akka.actor.{Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.{CourierJson, CourierUser, Creds}

import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/24/16.
  */

object CourierModule {
  case class AddCourier(courier: CourierJson, creds: Creds, businessId: Long)
  case class ChangeCourier(courier: CourierJson, businessId: Long)
  case class DeleteCourier(courierId: Long, businessId: Long)

  implicit val resolveTimeout = Timeout(300 seconds)
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  def props = Props[CourierActor]
}

class CourierActor extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  import CourierModule.AddCourier

  override def receive: Receive = {
    case AddCourier(courier, creds, businessId) =>
      if(!creds.businessIds.contains(businessId)) sender ! false
      else CourierUser.of(courier, businessId) match {
        case user: CourierUser =>
          user.save
          sender ! Business.addJobTo(courier.phone, businessId, Business.DSTypes.couriers)
        case _ => sender ! false
      }
  }
}



