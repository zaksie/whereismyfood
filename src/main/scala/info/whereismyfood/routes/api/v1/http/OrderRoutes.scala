package info.whereismyfood.routes.api.v1.http

import java.util.UUID

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.business.{BusinessScanner, NotifyChefs}
import info.whereismyfood.modules.order.OrderModule._
import info.whereismyfood.modules.order._
import info.whereismyfood.modules.user.Roles.api.order
import info.whereismyfood.modules.user.{AddProcessedOrders, Creds, Roles}
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import info.whereismyfood.modules.order.ProcessedOrderJsonSupport._

/**
  * Created by zakgoichman on 10/21/16.
  */
object OrderRoutes {
  val log = LoggerFactory.getLogger(this.getClass)
  val orderActorRef = Await.result(system.actorSelection("/user/modules/order").resolveOne(), resolveTimeout.duration)
  implicit val ec = system.dispatcher
  def routes(implicit creds: Creds) = {
    import Roles.api.order.{delete => delete_role, _}
    import info.whereismyfood.modules.order.OrdersJsonSupport._
    pathPrefix("orders") {
      pathEndOrSingleSlash {
        put {
          entity(as[Orders]) { orders =>
            log.info("In /orders PUT")
            complete {
              if (Roles.isauthorized(add, orders.businessId) || clientOwnsOrder(orders.orders))
                403
              else
                putOrders(orders) match {
                  case Left(l) =>
                    l.toJson.compactPrint
                  case Right(r) =>
                    HttpResponse(status = 400, entity = r.error)
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
                case "all" =>
                  log.info("In /orders/all GET")
                  complete {
                    (orderActorRef ? GetAllOrders(creds.businessIds.head))
                        .asInstanceOf[Future[Seq[ProcessedOrder]]]
                        .map(_.toJson.compactPrint)
                  }
                case "open" =>
                  log.info("In /orders/open GET")
                  complete {
                    (orderActorRef ? GetOpenOrders(creds.businessIds.head))
                        .asInstanceOf[Future[Seq[ProcessedOrder]]]
                        .map(_.toJson.compactPrint)
                  }
                case "closed" =>
                  log.info("In /orders/closed GET")
                  complete {
                    (orderActorRef ? GetReadyOrders(creds.businessIds.head))
                        .asInstanceOf[Future[Seq[ProcessedOrder]]]
                        .map(_.toJson.compactPrint)
                  }
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
                complete {
                  (orderActorRef ? MarkOrdersReady(businessId, mark.orderId))
                      .asInstanceOf[Future[Option[OrderReady]]]
                      .map {
                        case Some(orderReady) =>
                          println(orderReady)
                          HttpResponse(entity = orderReady.toJson.compactPrint)
                        case _ =>
                          HttpResponse(status = StatusCodes.NotFound)
                      }
                }
            }
          }
        }
      } ~
      path("me"){
        put {
          log.info("In orders/me PUT...")
          import OrderJsonSupport._
          entity(as[Order]) { order =>
            log.info("In orders/me PUT [V]")
            println(order)
            val id = if(order.id.isEmpty) UUID.randomUUID.toString else order.id
            val formatted_order = order.copy(id = id, client = creds,
              contents = order.contents.map(_.copy(orderId = Some(id), businessId = Some(order.businessId))))
            if (orderExists(formatted_order)) complete(403)
            else {
              val orders = Seq(formatted_order)
              if (clientOwnsOrder(orders))
                putOrders(Orders(orders.head.businessId, orders)) match {
                  case Left(seq) =>
                    seq match {
                      case Seq() =>
                        complete(HttpResponse(status = 400, entity = "Order put request contains already existing order ids"))
                      case processedOrders =>
                        removeOpenOrder(creds)
                        import ProcessedOrderJsonSupport._
                        complete(processedOrders.toJson.compactPrint)
                    }
                  case Right(err) =>
                    complete(HttpResponse(status = 400, entity = err.error))
                }
              else complete(403)
            }
          }
        }
      }
    }
  }
  def orderExists(order: Order): Boolean = {
    Await.result(orderActorRef ? DoesOrderExist(order), resolveTimeout.duration)
        .asInstanceOf[Boolean]
  }

  def putOrders(orders: Orders): Either[Seq[ProcessedOrder], OrderError] = {
    orders.isValid match {
      case OrderError.OK =>
        Left(Await.result(orderActorRef ? AddOrders(orders), resolveTimeout.duration)
            .asInstanceOf[Seq[ProcessedOrder]])
      case x: OrderError =>
        Right(x)
    }
  }

  def clientOwnsOrder(orders: Seq[Order])(implicit creds: Creds): Boolean = {
    orders.forall(x=>x.client.phone.isEmpty || x.client.phone == creds.phone)
  }

  def removeOpenOrder(creds: Creds) = {
    OpenOrder.remove(creds.phone)
  }
}
