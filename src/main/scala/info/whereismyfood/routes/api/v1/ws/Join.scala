package info.whereismyfood.routes.api.v1.ws

import akka.NotUsed
import akka.actor.PoisonPill
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.auth.{Creds, Roles}
import info.whereismyfood.libs.user.{ClientUserActor, CourierUserActor}
import info.whereismyfood.libs.user.UserActorUtils._

/**
  * Created by zakgoichman on 11/1/16.
  */
object Join {
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  def createUserActor(implicit creds: Creds) = {
    creds.role match {
      case Some(Roles.courier) =>
        Some(system.actorOf(CourierUserActor.props))
      case Some(Roles.client) =>
        Some(system.actorOf(ClientUserActor.props))
      case _ =>
        None
    }
  }

  def join(implicit creds: Creds): Flow[Message, Message, _] =
    createUserActor match {
      case Some(userActor) =>
        val incoming: Sink[Message, _] =
          Flow[Message].map {
            case TextMessage.Strict(text) => IncomingMessage(text)
          }.to(Sink.actorRef[IncomingMessage](userActor, PoisonPill))

        val outgoing: Source[Message, NotUsed] =
          Source.actorRef[OutgoingMessage](10, OverflowStrategy.fail)
            .mapMaterializedValue {
              outActor =>
                userActor ! Connected(outActor)
                NotUsed
            }.map {
            outMsg: OutgoingMessage => TextMessage(outMsg.text)
          }

        Flow.fromSinkAndSource(incoming, outgoing)
      case None =>
        Flow[Message].mapConcat {
          case tm: TextMessage =>
            tm.textStream.runWith(Sink.ignore)
            Nil
          case bm: BinaryMessage =>
            // ignore binary messages but drain content to avoid the stream being clogged
            bm.dataStream.runWith(Sink.ignore)
            Nil
        }
    }

  def routes(implicit creds: Creds) = {
    path("join") {
      handleWebSocketMessages(join)
    }
  }
}
