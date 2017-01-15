package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.order.OrderModule._
import info.whereismyfood.modules.order._
import info.whereismyfood.modules.user.{Creds, Roles}
import org.slf4j.LoggerFactory
import spray.json._
import java.util.UUID

import scala.concurrent.Await

/**
  * Created by zakgoichman on 10/21/16.
  */
object OrderRoutes {
  val log = LoggerFactory.getLogger(this.getClass)
  val orderActorRef = Await.result(system.actorSelection("/user/modules/order").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    import Roles.api.order.{delete => delete_role, _}
    import info.whereismyfood.modules.order.OrdersJsonSupport._
    pathPrefix("orders") {
      pathEndOrSingleSlash {
        put {
          entity(as[Orders]) { orders =>
            log.info("In /orders PUT")
            Roles.isauthorized(add, orders.businessId) match {
              case false =>
                if(clientOwnsOrder(orders.orders))
                  complete(putOrders(orders))
                else complete(403)
              case true =>
                complete(putOrders(orders))
            }
          }
        } ~
        get {
              log.info("In /orders GET")
              Roles.isauthorized(view, creds.businessIds.head) match {
                case false => complete(403)
                case true =>
                  parameter('type) {
                    case "open" =>
                      val res = Await.result(orderActorRef ? GetOpenOrders(creds.businessIds.head),
                        resolveTimeout.duration).asInstanceOf[Seq[ProcessedOrder]]
                      import info.whereismyfood.modules.order.ProcessedOrderJsonSupport._
                      complete(res.toJson)
                    case "enroute" =>
                      val res = Await.result(orderActorRef ? GetEnrouteOrders(creds.businessIds.head),
                        resolveTimeout.duration).asInstanceOf[Seq[ProcessedOrder]]
                      import info.whereismyfood.modules.order.ProcessedOrderJsonSupport._
                      complete(res.toJson)
                    case _ => complete(400)
                  }
              }
            }
      } ~
      path(LongNumber / Segment) { (businessId, orderId) =>
        delete {
          Roles.isauthorized(delete_role, businessId) match {
            case false => complete(403)
            case true =>
              log.info("In /orders DELETE")
              val ok = Await.result(orderActorRef ? DeleteOrders(businessId, orderId), resolveTimeout.duration).asInstanceOf[Boolean]
              complete(if (ok) 200 else 400)
          }
        }
      } ~
      path("mark-ready") {
        post {
          entity(as[OrderReady]) { mark =>
            val businessId = creds.businessIds.head
            Roles.isauthorized(markReady, businessId) match {
              case false => complete(403)
              case true =>
                log.info("In /orders/mark-ready")
                val ok = Await.result(orderActorRef ? MarkOrdersReady(businessId, mark.orderId, mark.ready), resolveTimeout.duration).asInstanceOf[Boolean]
                complete(if (ok) 200 else 400)
            }
          }
        }
      } ~
      path("me"){
        import OrderJsonSupport._
        put {
          entity(as[Order]) { order =>
            val orders = Seq(order.copy(id=UUID.randomUUID.toString, client=creds))
            if (clientOwnsOrder(orders))
              putOrders(Orders(order.businessId, orders)) match {
                case res if res.status == StatusCodes.OK =>
                  removeOpenOrder(creds)
                  complete(res)
                case res =>
                  complete(res)
              }
            else complete(403)
          }
        }
      }
    }
  }

  def putOrders(orders: Orders): HttpResponse = {
    orders.isValid match {
      case OrderError.OK =>
        Await.result(orderActorRef ? AddOrders(orders), resolveTimeout.duration)
            .asInstanceOf[Seq[ProcessedOrder]] match {
          case Seq() =>
            HttpResponse(status = 400, entity = "Order put request contains already existing order ids")
          case processedOrders =>
            println("yay!!")
            import ProcessedOrderJsonSupport._
            HttpResponse(entity = processedOrders.toJson.compactPrint)
        }
      case OrderError(err) =>
        HttpResponse(status = 400, entity = err)
    }
  }

  def clientOwnsOrder(orders: Seq[Order])(implicit creds: Creds): Boolean = {
    orders.forall(x=>x.client.phone.isEmpty || x.client.phone == creds.phone)
  }

  def removeOpenOrder(creds: Creds) = {
    OpenOrder.remove(creds.phone)
  }
}
