package info.whereismyfood.modules

import akka.actor.{Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.aux.MyConfig.Topics
import info.whereismyfood.libs.order.{DatabaseOrder, Order}

import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/24/16.
  */

object NewOrderModule {
  implicit val resolveTimeout = Timeout(300 seconds)
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  def props = Props[NewOrderActor]
}

class NewOrderActor extends Actor {
  val mediator = DistributedPubSub(context.system).mediator

  override def receive: Receive = {
    case order: Order =>
      val ok = DatabaseOrder.save(order)
      if(ok)
        mediator ! Publish(Topics.clientUpdates + order.recipient.phone, order)

      sender ! ok

  }
}



