package info.whereismyfood.modules.courier

import akka.actor.{Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.models.business.Business
import info.whereismyfood.models.user.{CourierJson, CourierUser}

import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/24/16.
  */

object CourierModule {
  case class AddCourier(courier: CourierJson, businessId: Long)
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
    case AddCourier(courier, businessId) =>
      CourierUser.of(courier, businessId) match {
        case user: CourierUser =>
          user.save
          sender ! Business.addCourierTo(courier.phone, businessId)
        case _ => sender ! false
      }
  }
}



