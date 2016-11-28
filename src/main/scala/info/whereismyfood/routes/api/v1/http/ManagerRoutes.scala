package info.whereismyfood.routes.api.v1.http


import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.StreamConverters
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.libs.storage.GoogleStorageClient
import info.whereismyfood.modules.user.{Creds, Roles}
import info.whereismyfood.modules.business.{AdminUserAssets, Business}
import info.whereismyfood.modules.courier.CourierModule.AddCourier
import info.whereismyfood.modules.user.{CourierJson, Creds, Roles}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}


/**
  * Created by zakgoichman on 11/11/16.
  */
object ManagerRoutes {
  val log = LoggerFactory.getLogger(this.getClass)

  val IntRegEx = "(\\d+)".r

  val courierActionActorRef = Await.result(system.actorSelection("/user/modules/courier-action").resolveOne(), resolveTimeout.duration)
  def routes(implicit creds: Creds) = pathPrefix("manage") {
    if (!Roles.isManager(creds.role))
      complete(401)
    else {
      path("business" / Segment){
        case "all" => AdminUserAssets.getAllFor(creds) match{
          case Some(assets) =>
            import info.whereismyfood.modules.business.AdminUserAssetsJsonSupport._
            complete(assets)
          case _ => complete(400)
        }
        case IntRegEx(businessId) =>
          complete(200)
        case _ => complete(400)
      } ~
      path("courier" / Segment) { phone =>
        println(phone)
        put {
          entity(as[Multipart.FormData]) { formData =>

            // collect all parts of the multipart as it arrives into a map
            val allPartsF: Future[Map[String, String]] = formData.parts.mapAsync[(String, String)](1) {

              case b: BodyPart if b.name == "file" =>
                // stream into a file as the chunks of it arrives and return a future
                // file to where it got stored
                val inputStream = b.entity.dataBytes.runWith(StreamConverters.asInputStream(30 seconds))
                Future(GoogleStorageClient.put("courier-photos", inputStream).map(filename =>
                  "file" -> filename).get)
              case b: BodyPart =>
                // collect form field values
                b.toStrict(2 seconds).map(strict =>
                  b.name -> strict.entity.data.utf8String)

            }.runFold(Map.empty[String, String])((map, tuple) => map + tuple)

            val result = allPartsF.flatMap { allParts =>
              val filePart: Option[String] = Try{allParts("file")}.toOption
              Try{
                AddCourier(CourierJson(
                  Some(allParts("name")),
                  allParts("phone"),
                  filePart), creds,
                  allParts("business").toLong
                )
              }.toOption match {
                case Some(addCourier) =>
                  courierActionActorRef ? addCourier
                case _ =>
                  Future.successful(false)
              }
            }

            onComplete(result) {
              case Success(x) =>
                x match{
                  case false => complete(400)
                  case true => complete(200)
                }
              case Failure(x) =>
                complete(500)
            }
          }
        } ~
        delete{
          entity(as[String]) { businessId =>
            Try{
              businessId.toLong
            }.toOption match {
              case Some(id) =>
                if (!creds.businessIds.contains(id)) complete(403)
                else {
                  Business.removeJobFrom(phone, id, Business.Jobs.couriers)
                  complete(200)
                }
              case _ =>
                complete(401)
            }
          }
        }
      } ~
      pathEndOrSingleSlash{
        complete(200)
      }
    }
  }
}

