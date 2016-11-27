package info.whereismyfood

import akka.actor.{Actor, ActorLogging, PoisonPill}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.Http
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.aux.MyConfig.ActorNames
import info.whereismyfood.modules.business.{BusinessScanner, ScanForBusinesses}
import info.whereismyfood.modules.user._
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
    WebServer.start(port)

    //TODO: either do something with this info or unsubscribe before going live
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])

    //Scans for changes in business records in db
    val ref1 = context.actorOf(ClusterSingletonManager.props(
      singletonProps = BusinessScanner.props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(context.system)),
      ActorNames.Bare.businessManager)

    println(s"BusinessScanner path: ${ref1.path}")

    val businessScanner = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = ActorNames.Paths.businessManager,
        settings = ClusterSingletonProxySettings(context.system)),
      name = ActorNames.Bare.businessManager+ "-proxy")

    import scala.concurrent.duration._
    context.system.scheduler.schedule(
      0 milliseconds,
      1 minute,
      businessScanner,
      ScanForBusinesses)(context.system.dispatcher)

    startUpLazyObjects()
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def startUpLazyObjects(): Unit = {
    import info.whereismyfood.modules.user._

    ClientUser.unlazy
    CourierUser.unlazy
    ManagerUser.unlazy
    APIUser.unlazy
    ChefUser.unlazy
  }
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
