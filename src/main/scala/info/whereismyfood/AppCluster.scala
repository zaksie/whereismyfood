package info.whereismyfood

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}


/**
  * Created by zakgoichman on 10/21/16.
  */
object AppCluster {
  def main(args: Array[String]): Unit = {
    //val ports = Seq((2551, 8082), (2552, 8083))
    // Create an Akka system
    //for(port <- ports){
      // Override the configuration of the port
//      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ports._1).
//        withFallback(ConfigFactory.load())
      implicit val system = ActorSystem("YummletSystem", MyConfig.config)
      ActorSystemContainer.init(ActorSystemContainer())

      // Create an actor that handles cluster domain events
      system.actorOf(Props(WebServerClusterListener(MyConfig.getInt("server.port"))), name = "cluster-node")
    //}
  }
}
