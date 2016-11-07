package info.whereismyfood.modules

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSub
import akka.util.Timeout
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import info.whereismyfood.libs.geo.BrowserGeolocation

import scala.concurrent.duration._
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import info.whereismyfood.aux.MyConfig.Topics

/**
  * Created by zakgoichman on 10/24/16.
  */

object LocationSharingModule {
  implicit val resolveTimeout = Timeout(300 seconds)
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  def props = Props[LocationSharingActor]
  def registerPosition(position: BrowserGeolocation) = {
    system.actorSelection("/user/modules/share-location") ! position
  }

}

object LocationSharingActor {
  case object Join
}

class LocationSharingActor extends Actor {
  val mediator = DistributedPubSub(context.system).mediator

  override def receive: Receive = {
    case position: BrowserGeolocation => {
      mediator ! Publish(Topics.courierGeolocation, position)
    }
  }
}



