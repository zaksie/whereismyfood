package info.whereismyfood.routes.internal

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.routes.api.v1.http.MenuRoutes

/**
  * Created by zakgoichman on 1/10/17.
  */
object Menu {
  def routes =
    path("menus" / LongNumber) { id =>
      MenuRoutes.getMenu(id)
    }
}
