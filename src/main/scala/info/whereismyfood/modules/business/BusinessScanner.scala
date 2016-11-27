package info.whereismyfood.modules.business

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import info.whereismyfood.aux.ActorSystemContainer

import scala.util.{Failure, Success}

/**
  * Created by zakgoichman on 11/16/16.
  */

case object ScanForBusinesses

object BusinessScanner {
  def props = Props[BusinessScanner]
}

class BusinessScanner extends Actor with ActorLogging{
  import scala.concurrent.ExecutionContext.Implicits._
  import ActorSystemContainer.Implicits._
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
