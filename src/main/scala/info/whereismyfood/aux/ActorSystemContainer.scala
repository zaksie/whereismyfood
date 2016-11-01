package info.whereismyfood.aux

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import info.whereismyfood.libs.LibActors
import info.whereismyfood.modules.ModuleActors

/**
  * Created by zakgoichman on 10/23/16.
  */
class ActorSystemContainer private() {
  private implicit val sys = ActorSystem.create("whereismyfood")
  private val materializer = ActorMaterializer()

  sys.actorOf(Props[LibActors], "libs")
  sys.actorOf(Props[ModuleActors], "modules")

  def getSystem = {
    sys
  }
  def getMaterializer = {
    materializer
  }
}
object ActorSystemContainer {
  val instance = new ActorSystemContainer


  def getInstance = synchronized {
    instance
  }

  def getSystem = synchronized {
    instance.sys
  }

  def getMaterializer = synchronized {
    instance.materializer
  }
}
