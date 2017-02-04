package info.whereismyfood.aux

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import info.whereismyfood.libs.LibActors
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.modules.ModuleActors

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/23/16.
  */
case class ActorSystemContainer(implicit system: ActorSystem) {

  private val materializer = ActorMaterializer()

  system.actorOf(Props[LibActors], "libs")
  system.actorOf(Props[ModuleActors], "modules")

  def getSystem = {
    system
  }
  def getMaterializer = {
    materializer
  }
}
object ActorSystemContainer {
  private var instance: ActorSystemContainer = _

  def init(sysContainer: ActorSystemContainer): Unit ={
    Option(instance) match{
      case None => instance = sysContainer
      case _ =>
    }
    Databases.init
  }


  def getInstance: ActorSystemContainer = synchronized {
    instance
  }

  def getSystem: ActorSystem = synchronized {
    instance.getSystem
  }

  def getMaterializer: ActorMaterializer = synchronized {
    instance.materializer
  }

  object Implicits {
    implicit val resolveTimeout = Timeout(60 seconds)
    implicit val system = ActorSystemContainer.getSystem
    implicit val materializer = ActorSystemContainer.getMaterializer

    implicit def Int2String(i: Int): ToResponseMarshallable = StatusCode.int2StatusCode(i)

    implicit def Boolean2Route(b: Boolean): ToResponseMarshallable = if (b) StatusCode.int2StatusCode(200) else StatusCode.int2StatusCode(403)

    def toResponse(value: Any): ToResponseMarshallable = value match {
      case n: Number => n.intValue
      case s: String => s
      case b: Boolean => b
      case _ => ""
    }
  }
}
