package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.auth.Creds
import info.whereismyfood.libs.order.Order
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by zakgoichman on 10/21/16.
  */
object ClientOrder {
  import info.whereismyfood.libs.order.OrderJsonSupport._

  implicit val resolveTimeout = Timeout(300 seconds)
  val log = LoggerFactory.getLogger(this.getClass)
  val system = ActorSystemContainer.getSystem
  val actorRef = Await.result(system.actorSelection("/user/modules/new-order").resolveOne(), resolveTimeout.duration)
  def routes(implicit creds: Creds) =
    path("client-order") {
      put {
        entity(as[Order]) { order =>
          val ok = Await.result(actorRef ? order, resolveTimeout.duration).asInstanceOf[Boolean]
          complete(if (ok) HttpResponse(StatusCodes.OK) else HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
}
