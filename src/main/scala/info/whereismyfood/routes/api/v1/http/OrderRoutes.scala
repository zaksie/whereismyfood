package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.models.order.{OrderError, OrderReady, Orders, ProcessedOrder}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import akka.pattern.ask
import info.whereismyfood.models.user.{APIUser, Creds, Roles}
import info.whereismyfood.modules.order.OrderModule.{AddOrders, DeleteOrders, GetOrders, MarkOrdersReady, ModifyOrders}
import spray.json._

/**
  * Created by zakgoichman on 10/21/16.
  */
object OrderRoutes {

  import info.whereismyfood.models.order.OrdersJsonSupport._

  val log = LoggerFactory.getLogger(this.getClass)
  val orderActorRef = Await.result(system.actorSelection("/user/modules/order").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    implicit val user = APIUser.of(creds)
    import Roles.api.order.{add, modify, delete => delete_role, markReady, view}
    pathPrefix("orders") {
      pathEndOrSingleSlash {
        put {
          entity(as[Orders]) { orders =>
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
              Roles.isauthorized(view, creds.businessIds.head) match {
                case false => complete(403)
                case true =>
                  val res = Await.result(orderActorRef ? GetOrders(creds.businessIds.head),
                    resolveTimeout.duration).asInstanceOf[Seq[ProcessedOrder]]
                  import info.whereismyfood.models.order.ProcessedOrderJsonSupport._
                  val json = res.toJson
                  println(json.prettyPrint)
                  complete(json)
              }
            }
      } ~
          path(LongNumber / Segment) { (businessId, orderId) =>
            delete {
              Roles.isauthorized(delete_role, businessId) match {
                case false => complete(403)
                case true =>
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
                    val ok = Await.result(orderActorRef ? MarkOrdersReady(businessId, mark.orderId, mark.ready), resolveTimeout.duration).asInstanceOf[Boolean]
                    complete(if (ok) 200 else 400)
                }
              }
            }
          }
    }
  }
}
