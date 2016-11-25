package info.whereismyfood.modules.userActors

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.models.user.GenericUser

import scala.collection.mutable
/**
  * Created by zakgoichman on 11/7/16.
  */
object UserActorUtils {
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
  case class Connected(actorRef: ActorRef)
  case class SubscribeToCourier(topic: String)
  case class UnsubscribeToCourier(topic: String)
  case class ClientUpdates(topicToFollow: String)

  abstract class Subscriptions(val actor: ActorRef)(implicit val user: GenericUser, implicit val mediator: ActorRef) {
    import ActorSystemContainer.Implicits._
    private val subscriptions: mutable.Set[String] = mutable.Set()
    implicit val executionContext = system.dispatcher

    def selfTopic: String

    mediator ? Subscribe(selfTopic, actor) map{
      case ack : SubscribeAck =>
        subscriptions += selfTopic
    }

    def +=(topic: String): Unit = {
      if(!subscriptions.contains(topic)) {
        subscriptions += topic
        mediator ! Subscribe(topic, actor)
      }
      /*(mediator ? GetTopics).map{
        case CurrentTopics(topics) =>
          update(topics)
      }*/
    }

    def -=(topic: String): Unit = {
      if(subscriptions.contains(topic)) {
        subscriptions -= topic
        mediator ! Unsubscribe(topic, actor)
      }
    }

    def update(currentTopics: Set[String]) ={
      (subscriptions -- currentTopics).foreach(mediator ! Subscribe(_, actor))
      (currentTopics -- subscriptions).foreach(mediator ! Unsubscribe(_, actor))
    }
  }
}
