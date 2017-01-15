package info.whereismyfood.routes.api.v1.http


import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.StreamConverters
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import info.whereismyfood.libs.storage.GoogleStorageClient
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.courier.CourierModule.AddCourier
import info.whereismyfood.modules.menu.DishModule.AddDish
import info.whereismyfood.modules.menu.MenuModule.AddMenu
import info.whereismyfood.modules.menu.{Dish, Menu, Price}
import info.whereismyfood.modules.user.{AdminUserAssets, CourierJson, Creds, Roles}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try


/**
  * Created by zakgoichman on 11/11/16.
  */
object ManagerRoutes {
  private val log = LoggerFactory.getLogger(this.getClass)

  private val IntRegEx = "(\\d+)".r

  private val courierActionActorRef = Await.result(system.actorSelection("/user/modules/couriers").resolveOne(), resolveTimeout.duration)
  private val dishActionActorRef = Await.result(system.actorSelection("/user/modules/dishes").resolveOne(), resolveTimeout.duration)
  private val menuActionActorRef = Await.result(system.actorSelection("/user/modules/menus").resolveOne(), resolveTimeout.duration)
  implicit val executionContext = ActorSystemContainer.getSystem.dispatcher

  def routes(implicit creds: Creds) = pathPrefix("manage") {
    if (!Roles.isManager(creds.role))
      complete(401)
    else {
      pathEndOrSingleSlash {
        complete(200)
      } ~
      path("business" / Segment) {
        case "all" => AdminUserAssets.getAllFor(creds) match {
          case Some(assets) =>
            import info.whereismyfood.modules.user.AdminUserAssetsJsonSupport._
            complete(assets)
          case _ => complete(400)
        }
        case IntRegEx(_) =>
          complete(200)
        case _ => complete(400)
      } ~
      path("courier" / Segment) { phone =>
        put {
          entity(as[Multipart.FormData]) { formData =>
            complete(Await.result[Int](addCourier(formData), 5 minutes))
          }
        } ~
            delete {
              entity(as[String]) {
                case IntRegEx(_id) =>
                  val id = _id.toLong
                  if (!creds.owns(id)) complete(403)
                  else {
                    Business.removeJobFrom(phone, id, Business.DSTypes.couriers)
                    complete(200)
                  }
                case _ => complete(400)
              }
            }
      } ~
      path("dish" / LongNumber) { dishId =>
        put {
          entity(as[Multipart.FormData]) { formData =>
            complete(addDish(formData))
          }
        } ~
            delete {
              Dish.find(dishId) match {
                case Some(dish) if creds.owns(dish.businessIds: _*) =>
                  complete {
                    if (Dish.remove(dishId)) 200 else 503
                  }
                case None => complete(400)
                case _ => complete(403)
              }
            }
      } ~
      path("menu" / LongNumber) { menuId =>
            put {
              entity(as[Multipart.FormData]) { formData =>
                complete(addMenu(formData))
              }
            } ~
                delete {
                  Menu.find(menuId) match {
                    case Some(menu) if creds.owns(menu.businessIds: _*) =>
                      complete {
                        if (Menu.remove(menuId)) 200 else 503
                      }
                    case None => complete(400)
                    case _ => complete(403)
                  }
                }
          }
    }
  }

  def parseMultiPart(folder: String, formData: Multipart.FormData): Future[Map[String, String]] = {
    // collect all parts of the multipart as it arrives into a map
    formData.parts.mapAsyncUnordered[(String, String)](1) {
      case b: BodyPart if b.name == "file" =>
        val inputStream = b.entity.dataBytes.runWith(StreamConverters.asInputStream(30 seconds))
        Future(GoogleStorageClient.put(b.filename.get, folder, inputStream).map(filename =>
          "file" -> filename).get)
      case b: BodyPart =>
        b.toStrict(10 seconds).map { strict =>
          b.name -> strict.entity.data.utf8String
        }
    }.runFold(Map.empty[String, String])((map, tuple) => map + tuple)
  }

  def addCourier(formData: Multipart.FormData)(implicit creds: Creds) = {
    def processData(allParts: Map[String, String]): Future[Int] = {
      val businessId = allParts("business").toLong
      if (!creds.owns(businessId))
        return Future.successful(403)
      Try {
        AddCourier(CourierJson(
          Some(allParts("name")),
          allParts("phone"),
          Some(allParts("file"))), creds,
          businessId
        )
      }.toOption match {
        case Some(addCourier) =>
          (courierActionActorRef ? addCourier).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
        case _ =>
          Future.successful(503)
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.couriers"), formData).flatMap(processData)
  }

  def addDish(formData: Multipart.FormData)(implicit creds: Creds): Future[String] = {
    def processData(allParts: Map[String, String]): Future[Int] = {
      try {
        val b = allParts("businesses")
        val businessIds: Seq[Long] = b.split(",").map(_.toLong)
        if (!creds.owns(businessIds: _*))
          return Future.successful(403)

        val addDish = AddDish(Dish(allParts("id").toLong,
          businessIds,
          allParts("title"),
          Try(allParts("file")).getOrElse(""),
          allParts("description"),
          Price(allParts("priceValue").toDouble, allParts("priceCurrency")))
        )
        (dishActionActorRef ? addDish).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
      } catch {
        case e: Throwable =>
          println("Error adding dish: " + e.getMessage)
          Future.successful(503)
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.dishes"), formData).flatMap(processData).map(_.toString)
  }

  def addMenu(formData: Multipart.FormData)(implicit creds: Creds): Future[String] = {
    def processData(allParts: Map[String, String]): Future[Int] = {
      try {
        val b = allParts("businesses")
        val businessIds: Seq[Long] = b.split(",").map(_.toLong)
        if (!creds.owns(businessIds: _*))
          return Future.successful(403)

        val addMenu = AddMenu(allParts("file"), allParts("json"), businessIds)
        (menuActionActorRef ? addMenu).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
      } catch {
        case e: Throwable =>
          println("Error adding menu: " + e.getMessage)
          Future.successful(503)
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.menus"), formData).flatMap(processData).map(_.toString)
  }
}
