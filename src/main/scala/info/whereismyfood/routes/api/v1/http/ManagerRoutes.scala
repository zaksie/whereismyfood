package info.whereismyfood.routes.api.v1.http


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.{HttpResponse, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.StreamConverters
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import info.whereismyfood.libs.storage.GoogleStorageClient
import info.whereismyfood.modules.business.{Business, BusinessInfo, BusinessJsonSupport, PatchBusinessInfo}
import info.whereismyfood.modules.geo.{Address, FailPinpointAddressException, LatLng}
import info.whereismyfood.modules.menu.DishModule.AddDish
import info.whereismyfood.modules.menu.MenuModule.AddMenu
import info.whereismyfood.modules.menu.{Dish, Menu, Price}
import info.whereismyfood.modules.user.AdminUserAssets.{GetBusinesses, GetDishes, GetMenus}
import info.whereismyfood.modules.user.ChefModule.{RequestToken, UpdateChef}
import info.whereismyfood.modules.user._
import org.slf4j.LoggerFactory
import spray.json._

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
  private val businessActionActorRef = Await.result(system.actorSelection("/user/modules/business").resolveOne(), resolveTimeout.duration)
  private val managerActionActorRef = Await.result(system.actorSelection("/user/modules/managers").resolveOne(), resolveTimeout.duration)
  private val chefActionActorRef = Await.result(system.actorSelection("/user/modules/chefs").resolveOne(), resolveTimeout.duration)
  private val adminActionActorRef = Await.result(system.actorSelection("/user/modules/admin").resolveOne(), resolveTimeout.duration)

  implicit val executionContext = ActorSystemContainer.getSystem.dispatcher

  def routes(implicit creds: Creds) = pathPrefix("manage") {
    if (!Roles.isManager(creds.role))
      complete(401)
    else {
      pathEndOrSingleSlash {
        complete(200)
      } ~
      pathPrefix("business"){
        import info.whereismyfood.modules.user.AdminUserAssets._
        import AdminUserAssetsJsonSupport._
        path("businesses") {
          complete{
            (adminActionActorRef ? GetBusinesses(creds)).asInstanceOf[Future[AdminUserAssets]].map(_.toJson.compactPrint)
          }
        } ~
            path("menus") {
              complete{
                (adminActionActorRef ? GetMenus(creds)).asInstanceOf[Future[AdminUserAssets]].map(_.toJson.compactPrint)
              }
            } ~
            path("dishes") {
              complete{
                (adminActionActorRef ? GetDishes(creds)).asInstanceOf[Future[AdminUserAssets]].map(_.toJson.compactPrint)
              }
            } ~
            path("owners") {
              complete{
                (adminActionActorRef ? GetOwners(creds)).asInstanceOf[Future[AdminUserAssets]].map(_.toJson.compactPrint)
              }
            } ~
            path("couriers") {
              complete{
                (adminActionActorRef ? GetCouriers(creds)).asInstanceOf[Future[AdminUserAssets]].map(_.toJson.compactPrint)
              }
            } ~
            path("terminals") {
              complete{
                (adminActionActorRef ? GetChefs(creds)).asInstanceOf[Future[AdminUserAssets]].map(_.toJson.compactPrint)
              }
            } ~
        path(LongNumber) { id =>
          put {
            entity(as[Multipart.FormData]) { formData =>
              complete(patchBusiness(formData, id))
            }
          }
        }
      } ~
      path("courier" / Segment) { phone =>
        put {
          entity(as[Multipart.FormData]) { formData =>
            complete(addCourier(formData))
          }
        }
      } ~
      //remove the nonsense about IntRegEx
      path("owner" / Segment) { phone =>
        put {
          entity(as[Multipart.FormData]) { formData =>
            complete(addOwner(formData))
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
                    if (Dish.remove(dishId)) 200 else 500
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
                        if (Menu.remove(menuId)) 200 else 500
                      }
                    case None => complete(400)
                    case _ => complete(403)
                  }
                }
          } ~
      pathPrefix("terminal"){
        path("request-token") {
          post {
            complete {
              (chefActionActorRef ? RequestToken)
                  .flatMap[ToResponseMarshallable] {
                case Some(id) => Future.successful(id.toString)
                case None => Future.successful(500)
              }
            }
          }
        } ~
        path(Segment) { chefId =>
          put {
            import UserJsonSupport._
            entity(as[UserJson]) { user =>
              complete(addChef(user))
            }
          } ~
              delete {
                ChefUser.find(chefId) match {
                  case Some(chef) if creds.owns(chef.businessIds.toSeq: _*) =>
                    complete {
                      if (chef.removeFromDatastore()) 200 else 500
                    }
                  case None => complete(400)
                  case _ => complete(403) // Some(_) but !creds.owns
                }
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


  def createUserJson(allParts: Map[String, String])(implicit creds: Creds): Either[Int, UserJson]  = {
    println("In createUserJson")
    val b = allParts("businessIds")
    val businessIds: Set[Long] = if(b.isEmpty) Set() else b.split(",").map(_.toLong).toSet
    if (!creds.owns(businessIds.toSeq: _*))
      Left(403)
    else
      Right(UserJson(
        allParts("name"),
        allParts("phone"),
        allParts("email"),
        Try(allParts("file")).toOption,
        Try(allParts("vehicleType")).toOption,
        businessIds,
        creds.businessIds.diff(businessIds),
        Some(allParts("prevPhone"))
      ))
  }
  def addCourier(formData: Multipart.FormData)(implicit creds: Creds): Future[ToResponseMarshallable] = {
    def processData(allParts: Map[String, String]): Future[ToResponseMarshallable] = {
      createUserJson(allParts) match{
        case Left(code) =>
          Future.successful(code)
        case Right(userJson) =>
          Try {
            AddUser(userJson)
          }.toOption match {
            case Some(add_courier) =>
              (courierActionActorRef ? add_courier).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
            case _ =>
              Future.successful(500)
          }
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.users"), formData).flatMap(processData)
  }
  def addOwner(formData: Multipart.FormData)(implicit creds: Creds): Future[ToResponseMarshallable] = {
    def processData(allParts: Map[String, String]): Future[ToResponseMarshallable] = {
      createUserJson(allParts) match{
        case Left(code) =>
          Future.successful(code)
        case Right(userJson) =>
          if(userJson.phone == creds.phone && userJson.businessIdsToRemove.nonEmpty)
            return Future.successful(405)

          Try {
            AddUser(userJson)
          }.toOption match {
            case Some(add_owner) =>
              (managerActionActorRef ? add_owner).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
            case _ =>
              Future.successful(500)
          }
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.users"), formData).flatMap(processData)
  }


  def addDish(formData: Multipart.FormData)(implicit creds: Creds): Future[ToResponseMarshallable] = {
    def processData(allParts: Map[String, String]): Future[ToResponseMarshallable] = {
      try {
        val b = allParts("businessIds")
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
          Future.successful(500)
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.dishes"), formData).flatMap(processData)
  }

  def addMenu(formData: Multipart.FormData)(implicit creds: Creds): Future[ToResponseMarshallable] = {
    def processData(allParts: Map[String, String]): Future[ToResponseMarshallable] = {
      try {
        val b = allParts("businessIds")
        val businessIds: Seq[Long] = b.split(",").map(_.toLong)
        if (!creds.owns(businessIds: _*))
          return Future.successful(403)

        val addMenu = AddMenu(Try(allParts("file")).toOption, allParts("json"), businessIds)
        (menuActionActorRef ? addMenu).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
      } catch {
        case e: Throwable =>
          println("Error adding menu: " + e.getMessage)
          Future.successful(500)
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.menus"), formData).flatMap(processData)
  }

  def addChef(chef: UserJson)(implicit creds: Creds): Future[ToResponseMarshallable] = {
    if (!creds.owns(chef.businessIds.head))
      return Future.successful(403)
    val chef_tag = chef.copy(businessIdsToRemove = creds.businessIds.diff(chef.businessIds))
    (chefActionActorRef ? UpdateChef(chef_tag)).map{
      case true => 200
      case _ => 400
    }
  }


  def patchBusiness(formData: Multipart.FormData, id: Long)(implicit creds: Creds): Future[ToResponseMarshallable] = {
    def processData(allParts: Map[String, String]): Future[ToResponseMarshallable] = {
      try {
        if (!creds.owns(id))
          return Future.successful(403)

        import BusinessJsonSupport._
        val info_tmp: BusinessInfo = allParts("info").parseJson.convertTo[BusinessInfo]

        val info = info_tmp.copy(image = Try(allParts("file")).getOrElse(info_tmp.image))
        val patch = PatchBusinessInfo(id, info)
        (businessActionActorRef ? patch).asInstanceOf[Future[Boolean]].map(res => if (res) 200 else 400)
      } catch {
        case FailPinpointAddressException =>
          Future.successful(HttpResponse(status = 400, entity= "Failed to pinpoint address"))
        case e: Throwable =>
          println("Error adding menu: " + e.getMessage)
          Future.successful(500)
      }
    }

    parseMultiPart(MyConfig.get("bucket-folders.businesses"), formData).flatMap(processData)
  }
}
