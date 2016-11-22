package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.models.order.{OrderReady, Orders}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import akka.pattern.ask
import info.whereismyfood.models.user.{APIUser, Creds, Roles}
import info.whereismyfood.modules.order.OrderModule.{AddOrders, DeleteOrders, MarkOrdersReady, ModifyOrders}

/**
  * Created by zakgoichman on 10/21/16.
  */
object OrderRoutes {

  import info.whereismyfood.models.order.OrdersJsonSupport._

  val log = LoggerFactory.getLogger(this.getClass)
  val orderActorRef = Await.result(system.actorSelection("/user/modules/order").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    implicit val user = APIUser.of(creds)
    import Roles.api.order.{add, modify, delete => _delete, markReady}
    path("orders") {
      put {
        entity(as[Orders]) { orders =>
          Roles.isauthorized(add, orders.businessId) match
          {
            case false => complete(403)
            case true =>
              val ok = orders.isValid |
                Await.result(orderActorRef ? AddOrders(orders), resolveTimeout.duration).asInstanceOf[Boolean]
              complete(if (ok) 200 else 400)
          }
        }
      } ~
        patch {
          entity(as[Orders]) { orders =>
            Roles.isauthorized(modify, orders.businessId) match {
              case false => complete(403)
              case true =>
                val ok = Await.result(orderActorRef ? ModifyOrders(orders), resolveTimeout.duration).asInstanceOf[Boolean]
                complete(if (ok) 200 else 400)
            }
          }
        }
    } ~
      path("orders" / LongNumber / Segment) { (businessId, orderId) =>
        delete {
          Roles.isauthorized(_delete, businessId) match {
            case false => complete(403)
            case true =>
              val ok = Await.result(orderActorRef ? DeleteOrders(businessId, orderId), resolveTimeout.duration).asInstanceOf[Boolean]
              complete(if (ok) 200 else 400)
          }
        } ~
          post {
            entity(as[OrderReady]) { mark =>
              Roles.isauthorized(markReady, businessId) match {
                case false => complete(403)
                case true =>
                  val ok = Await.result(orderActorRef ? MarkOrdersReady(businessId, orderId, mark.ready), resolveTimeout.duration).asInstanceOf[Boolean]
                  complete(if (ok) 200 else 400)
              }
            }
          }
      }
  }
}
