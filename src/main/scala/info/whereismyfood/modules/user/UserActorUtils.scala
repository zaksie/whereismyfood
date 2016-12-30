package info.whereismyfood.modules.user

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer
import spray.json.{DefaultJsonProtocol, JsonParser}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
/**
  * Created by zakgoichman on 11/7/16.
  */
object UserActorUtils {
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
  case class Connected(actorRef: ActorRef)

  case class Op(op: String, payload: String)
  object OpJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val opFormatter = jsonFormat2(Op)
  }
  object Ops{
    def parse(text: String): Op = {
      import OpJsonSupport._
      JsonParser(text).convertTo[Op]
    }
    val geolocation = "geolocation"
  }

  abstract class Subscriptions(val actor: ActorRef)(implicit val user: GenericUser, implicit val mediator: ActorRef) {
    import ActorSystemContainer.Implicits._
    private val subscriptions: mutable.Set[String] = mutable.Set()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

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