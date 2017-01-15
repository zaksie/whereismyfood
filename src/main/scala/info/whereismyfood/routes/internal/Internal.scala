package info.whereismyfood.routes.internal

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.aux.MyConfig

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
          parameter('secret){
            case x if x == MyConfig.get("internal.secret") =>
              MenuRoutesInt.routes
            case _ =>
              println("Invalid INTERNAL SECRET !!!")
              complete(403)
          }
        case other =>
          println(other)
          complete(403)
      }
    }
}
