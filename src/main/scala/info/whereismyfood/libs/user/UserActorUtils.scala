package info.whereismyfood.libs.user

import akka.actor.ActorRef
import akka.pattern.ask
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.auth.Creds

import scala.collection.mutable
import scala.concurrent.duration._
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

  abstract class Subscriptions(val actor: ActorRef)(implicit val creds: Creds, implicit val mediator: ActorRef) {
    private val subscriptions: mutable.Set[String] = mutable.Set()
    implicit val resolveTimeout = Timeout(30 seconds)
    implicit val system = ActorSystemContainer.getSystem
    implicit val materializer = ActorSystemContainer.getMaterializer
    implicit val executionContext = system.dispatcher

    def selfTopic: String

    (mediator ? Subscribe(selfTopic, actor)).map{
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