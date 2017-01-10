package info.whereismyfood.routes.internal

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._

/**
  * Created by zakgoichman on 10/20/16.
  */
object Internal {
  def routes =
    pathPrefix("internal") {
      println("In internal")
      extractHost {
        case "localhost" =>
          println("is localhost!")
          Menu.routes
        case other =>
          println(other)
          complete(403)
      }
    }
}
