package routes.api.v1.mock

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

/**
  * Created by zakgoichman on 10/20/16.
  */
object SayHello {
  val routes =
    path("sayhello") {
        complete(StatusCodes.OK)
    }
}
