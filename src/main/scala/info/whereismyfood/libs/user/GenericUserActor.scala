package info.whereismyfood.libs.user

import akka.actor.ActorRef

/**
  * Created by zakgoichman on 11/7/16.
  */
object GenericUserActor {
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
  case class Connected(actorRef: ActorRef)
  case class SubscribeToCourier(topic: String)
  case class UnsubscribeToCourier(topic: String)
}
