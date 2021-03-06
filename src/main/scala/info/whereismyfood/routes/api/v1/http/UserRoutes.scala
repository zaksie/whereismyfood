package info.whereismyfood.routes.api.v1.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.device.DeviceInfoJson
import info.whereismyfood.modules.user.ClientModule._
import info.whereismyfood.modules.user.{Creds, GenericUser, UserRouter}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.util.Random
import akka.pattern.ask
import info.whereismyfood.modules.payment.{CreditCard, PaymentMethod}
/**
  * Created by zakgoichman on 10/21/16.
  */
object UserRoutes {
  private val log = LoggerFactory.getLogger(this.getClass)
  private val clientActionActorRef = Await.result(system.actorSelection("/user/modules/clients").resolveOne(), resolveTimeout.duration)
  implicit val ec = system.dispatcher
  def routes(implicit creds: Creds) = pathPrefix("users"){
    (path("is" / Segment) & post) { job =>
      complete {
        UserRouter.getByJob(job) match {
          case Some(userCompObj) =>
            userCompObj.handshake(creds)
          case _ =>
            400
        }
      }
    } ~
    pathPrefix("me") {
          pathEndOrSingleSlash {
            GenericUser.getById(creds.phone) match {
              case Some(user) => complete(user.getInfoJson)
              case _ => complete(500)
            }
          } ~
          path("finances"){
            //TODO: Stub
            val r = Random.nextInt(100000)/100f
            complete(s"""{"runningTotal": $r}""")
          } ~
          pathPrefix("payment-methods"){
            pathEndOrSingleSlash {
              get {
                complete {
                  (clientActionActorRef ? GetPaymentMethods(creds)).map(toResponse)
                }
              }
            } ~
            path("credit-card") {
              put {
                println("In credit-card")
                import info.whereismyfood.modules.payment.CreditCardJsonSupport._
                entity(as[CreditCard]) { cc =>
                  complete {
                    (clientActionActorRef ? AddCreditCard(cc, creds)).map(toResponse)
                  }
                }
              }
            }
          }
        } ~
    pathPrefix("device"){
      pathPrefix("supports"){
        path("nfc"){
          import info.whereismyfood.modules.device.DeviceInfoJsonSupport._
          entity(as[DeviceInfoJson]){deviceInfo =>
            complete(deviceInfo.supportsNFC)
          }
        }
      }
    }
  }
}
