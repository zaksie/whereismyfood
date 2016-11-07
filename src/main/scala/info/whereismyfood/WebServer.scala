package info.whereismyfood

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.http.scaladsl.Http
import info.whereismyfood.aux.{ActorSystemContainer}
import info.whereismyfood.routes.Routes
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/21/16.
  */
object WebServer {
  def start(port: Int) {

    val log = LoggerFactory.getLogger(this.getClass)

    implicit val system = ActorSystemContainer.getSystem
    implicit val materializer = ActorSystemContainer.getMaterializer
    implicit val executionContext = system.dispatcher

    val bindingFuture = Http().bindAndHandle(Routes.routes, "localhost", port)

    println(s"Server online at http://localhost:"+port+"/\nPress RETURN phone stop...")
    bindingFuture.onFailure {
      case ex: Exception =>
        log.error("Failed phone bind phone port " + port, ex)
        System.exit(1)
    }
  }
}

case class WebServerClusterListener(port: Int) extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    //#subscribe
    WebServer.start(port)
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])

    //#subscribe
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      //log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      //log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      //log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case _: MemberEvent => // ignore
  }
}
