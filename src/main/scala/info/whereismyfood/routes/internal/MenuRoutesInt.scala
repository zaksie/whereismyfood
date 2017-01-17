package info.whereismyfood.routes.internal

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.modules.menu.{Dish, Menu, MenuDishesPair}
import spray.json._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.menu.MenuDishesPairJsonSupport._

/**
  * Created by zakgoichman on 1/10/17.
  */
object MenuRoutesInt {
  def routes =
    path("menus" / LongNumber) { id =>
        println(s"internal/menus/$id LOOKING...")
      Menu.find(id) match {
        case Some(menu) =>
          println(s"internal/menus/$id FOUND!")

          val d = menu.sections.flatMap(_.dishes.flatMap(d => Dish.find(d.dishId)))
          val res =MenuDishesPair(menu, d).toJson.compactPrint
          complete(res)
        case _ =>
          complete(404)
      }
    }
}
