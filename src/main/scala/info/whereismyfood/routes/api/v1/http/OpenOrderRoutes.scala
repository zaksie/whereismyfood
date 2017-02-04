package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, get, path, pathPrefix, put}
import akka.http.scaladsl.server.PathMatchers.{LongNumber, Segment}
import info.whereismyfood.aux.ActorSystemContainer.Implicits.{resolveTimeout, system}
import info.whereismyfood.modules.menu.DishToAdd
import info.whereismyfood.modules.order.{OpenOrder, OpenOrderJsonSupport, OrderItem, OrderItemJsonSupport}
import info.whereismyfood.modules.order.OrderModule.{DeleteOrderItemForUser, GetOpenOrderForUser, PutOrderItemForUser}
import info.whereismyfood.modules.user.Creds
import org.slf4j.LoggerFactory

import akka.http.scaladsl.server.Directives._

import scala.concurrent.Await
import spray.json._
import akka.pattern.ask
import info.whereismyfood.aux.ActorSystemContainer.Implicits._

/**
  * Created by zakgoichman on 1/2/17.
  */
object OpenOrderRoutes {
  val log = LoggerFactory.getLogger(this.getClass)
  val orderActorRef = Await.result(system.actorSelection("/user/modules/order").resolveOne(), resolveTimeout.duration)

  def routes(implicit creds: Creds) = {
    pathPrefix("orders" / "open" / "me") {
      pathEndOrSingleSlash {
        get {
          log.info(s"GET In /orders/open/me[${creds.phone}]")
          Await.result(orderActorRef ? GetOpenOrderForUser(creds), resolveTimeout.duration)
              .asInstanceOf[Option[OpenOrder]] match {
            case None => complete("{}")
            case Some(order) =>
              import OpenOrderJsonSupport._
              complete(order.toJson.compactPrint)
          }
        } ~
            put {
              import info.whereismyfood.modules.menu.DishJsonSupport._
              log.info("In orders/open/me PUT")
              entity(as[DishToAdd]) { item =>
                log.info(s"PUT In /orders/open/me[${creds.phone}]")
                Await.result(orderActorRef ? PutOrderItemForUser(creds, item), resolveTimeout.duration)
                    .asInstanceOf[Option[OrderItem]] match {
                  case Some(res) =>
                    import OrderItemJsonSupport._
                    complete(res.toJson.compactPrint)
                  case _ => complete(400)
                }
              }
            }
      } ~
          (delete & path(Segment)) { itemId =>
            log.info(s"DELETE In /orders/open/me[${creds.phone}]/itemId[$itemId]")
            if (Await.result(orderActorRef ? DeleteOrderItemForUser(creds, itemId), resolveTimeout.duration)
                .asInstanceOf[Boolean]) {
              complete(200)
            } else complete(400)
          }
    }
  }
}
