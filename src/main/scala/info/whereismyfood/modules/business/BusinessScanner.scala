package info.whereismyfood.modules.business

import akka.actor.{Actor, ActorContext, ActorLogging, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.aux.MyConfig.ActorNames

/**
  * Created by zakgoichman on 11/16/16.
  */

case object ScanForBusinesses

object BusinessScanner {
  def start(implicit context: ActorContext, cluster: Cluster) ={
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
  }
  def props = Props[BusinessScanner]
}

class BusinessScanner extends Actor with ActorLogging{
  import ActorSystemContainer.Implicits._

  import scala.concurrent.ExecutionContext.Implicits._
  override def receive: Receive = {
    case ScanForBusinesses =>
      Business.getAll foreach{ business =>
        val name = BusinessSingleton.getName(business.id)
        context.actorSelection(name).resolveOne().onFailure{
          case _ =>
            context.actorOf(ClusterSingletonManager.props(
              singletonProps = BusinessSingleton.props(business),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)),
              name)
        }
      }
    case x => println(x)
  }
}
