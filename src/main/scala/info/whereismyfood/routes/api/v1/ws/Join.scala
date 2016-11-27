package info.whereismyfood.routes.api.v1.ws

import akka.NotUsed
import akka.actor.{ActorRef, PoisonPill}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.modules.user.UserActorUtils._
import info.whereismyfood.modules.user._
import info.whereismyfood.modules.user.{Creds, UserRouter}

/**
  * Created by zakgoichman on 11/1/16.
  */
object Join {
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer


  def join(userActor: ActorRef): Flow[Message, Message, _] = {
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
  }

  def ignore: Flow[Message, Message, _] = {
    Flow[Message].mapConcat {
      x =>
        x.asBinaryMessage.getStreamedData.runWith(Sink.ignore, materializer)
        Nil
      //TODO: Remove the commented out section below if works
      /*
      case tm: TextMessage =>
        tm.textStream.runWith(Sink.ignore)
        Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
        */
    }
  }

  def routes(implicit creds: Creds) = {
    path("join" / Segment) { job =>
      handleWebSocketMessages {
        UserRouter.getByJob(job) match {
          case Some(userFactory) =>
            userFactory.createWebSocketActor(creds) match {
              case Some(userActor) => join(userActor)
              case _ => ignore
            }
          case _ => ignore
        }
      }
    }
  }
}
