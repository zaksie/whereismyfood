package info.whereismyfood.modules.menu

import akka.actor.{Actor, Props}
import info.whereismyfood.modules.menu.MenuJsonSupport._
import spray.json._
/**
  * Created by zakgoichman on 10/24/16.
  */

object MenuModule {
  case class AddMenu(image: Option[String], json: String, businessIds: Seq[Long])

  def props = Props[MenuActor]
}

class MenuActor extends Actor {
  import MenuModule._

  override def receive: Receive = {
    case AddMenu(image, json, businessIds) =>
      println(json)
      try{
        val jsonEx = json.subSequence(0, json.length - 1) + s""", "image": "$image"} """
        val menu_tmp = jsonEx.parseJson.convertTo[Menu]
        Menu.getFromDatastore(menu_tmp.id) match {
          case Some(menu) =>
            menu_tmp.copy(image = image.getOrElse(menu.image), businessIds = businessIds).saveToDatastore().get
          case _ =>
            menu_tmp.copy(image = image.getOrElse(""), businessIds = businessIds).saveToDatastore().get
        }
        sender ! true
      }catch{
        case e:Throwable =>
          println(e)
          sender ! false
      }
  }
}



