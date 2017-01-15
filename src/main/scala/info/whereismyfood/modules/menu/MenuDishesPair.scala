package info.whereismyfood.modules.menu

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 1/15/17.
  */
case class MenuDishesPair(menu: Menu, dishes: Seq[Dish])

object MenuDishesPairJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import MenuJsonSupport._
  import DishJsonSupport._
  implicit val menuDishesPairFormat = jsonFormat2(MenuDishesPair)
}