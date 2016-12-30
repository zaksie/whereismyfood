package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import akka.pattern.ask
import info.whereismyfood.modules.user.{APIUser, Creds, Roles, UserRouter}
import info.whereismyfood.modules.order.OrderModule._
import info.whereismyfood.modules.order._
import spray.json._

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
              case false => complete(403)
              case true =>
                orders.isValid match {
                  case OrderError.OK =>
                    Await.result(orderActorRef ? AddOrders(orders), resolveTimeout.duration) match {
                      case true =>
                        complete(200)
                      case _ =>
                        complete(HttpResponse(status = 400, entity = "Order put request contains already existing order ids"))
                    }
                  case OrderError(err) =>
                    complete(HttpResponse(status = 400, entity = err))
                }

            }
          }
        } ~
            patch {
              entity(as[Orders]) { orders =>
                log.info("In /orders PATCH")
                Roles.isauthorized(modify, orders.businessId) match {
                  case false => complete(403)
                  case true =>
                    Await.result(orderActorRef ? ModifyOrders(orders), resolveTimeout.duration) match {
                      case true =>
                        complete(200)
                      case _ =>
                        complete(HttpResponse(status = 400, entity = "Order patch request contains non-existing order ids"))
                    }
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
      pathPrefix("open" / "this-user" / LongNumber) { businessId =>
        (get & pathEndOrSingleSlash) {
          log.info(s"GET In /orders/open/this-user[${creds.phone}]/businessId[$businessId]")
          Await.result(orderActorRef ? GetOpenOrderForUser(businessId, creds), resolveTimeout.duration)
              .asInstanceOf[Option[OpenOrder]] match {
            case Some(order) =>
              import OpenOrderJsonSupport._
              complete(order.toJson.compactPrint)
            case _ => complete(404)
          }
        } ~
        pathPrefix("item") {
          (put & pathEndOrSingleSlash) {
            import OrderJsonSupport._
            entity(as[OrderItem]) { item =>
              log.info(s"PUT In /orders/open/this-user[${creds.phone}]/businessId[$businessId]")
              if (Await.result(orderActorRef ? PutOrderItemForUser(businessId, creds, item), resolveTimeout.duration)
                  .asInstanceOf[Boolean]) {
                complete(200)
              } else complete(400)
            }
          } ~
          (delete & path(Segment)) { itemId =>
            log.info(s"DELETE In /orders/open/this-user[${creds.phone}]/businessId[$businessId]/itemId[$itemId]")
            if (Await.result(orderActorRef ? DeleteOrderItemForUser(businessId, creds, itemId), resolveTimeout.duration)
                .asInstanceOf[Boolean]) {
              complete(200)
            } else complete(400)
          }
        }
      }
    }
  }
}
