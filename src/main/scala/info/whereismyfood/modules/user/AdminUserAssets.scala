package info.whereismyfood.modules.user

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.menu.{Dish, Menu}
import info.whereismyfood.modules.user.AdminUserAssets._
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/16/16.
  */

object AdminUserAssets {

  case class Businesses(businesses: Set[Business], ids: Set[Long])

  case class GetMenus(creds: Creds)

  case class GetDishes(creds: Creds)

  case class GetCouriers(creds: Creds)

  case class GetOwners(creds: Creds)

  case class GetChefs(creds: Creds)

  case class GetBusinesses(creds: Creds)

  def props = Props[AdminUserAssetsActor]

  //TODO: refactor with separate endpoint for each asset
  def getAllFor(implicit creds: Creds): AdminUserAssets = {
    val b = getBusinessesInternal
    val couriers = getCouriers
    val dishes = getDishes
    val menus = getMenus
    val chefs = getChefs
    val owners = getOwners

    AdminUserAssets(b.businesses, couriers.couriers, dishes.dishes, menus.menus, owners.owners, chefs.terminals)
  }

  private def getBusinessesInternal(implicit creds: Creds): Businesses = {
    val businesses = Business.getAllFor(creds.phone, Business.DSTypes.owners)
    val businessIds = businesses.map(_.id)
    Businesses(businesses, businessIds)
  }

  def getBusinesses(implicit creds: Creds): AdminUserAssets = {
    val b = getBusinessesInternal
    AdminUserAssets(b.businesses)
  }

  def getCouriers(implicit creds: Creds): AdminUserAssets = {
    val b = getBusinessesInternal
    val couriers: Set[UserJson] = CourierUser.getById(b.businesses.flatMap(_.couriers).toSeq: _*).map(_.toJson(b.ids)).toSet
    AdminUserAssets(businesses = b.businesses, couriers = couriers)
  }

  def getOwners(implicit creds: Creds): AdminUserAssets = {
    val b = getBusinessesInternal
    val owners: Set[UserJson] = ManagerUser.getById(b.businesses.flatMap(_.owners).toSeq: _*).map(_.toJson(b.ids)).toSet
    AdminUserAssets(businesses = b.businesses, owners = owners)
  }

  def getChefs(implicit creds: Creds): AdminUserAssets = {
    val b = getBusinessesInternal
    val chefs: Set[UserJson] = ChefUser.getById(b.businesses.flatMap(_.chefs).toSeq: _*)
        .filter(x => x.verified || !x.isTransactionalExpired)
        .map(x => x.toJson(b.ids, x.getTransactionalExpiry))
        .toSet
    AdminUserAssets(businesses = b.businesses, terminals = chefs)
  }

  def getDishes(implicit creds: Creds): AdminUserAssets = {
    val bs = getBusinessesInternal
    val dishes: Set[Dish] = bs.businesses.flatMap(b => Dish.getRecordsByBusinessId(b.id))
    AdminUserAssets(businesses = bs.businesses, dishes = dishes)
  }

  def getMenus(implicit creds: Creds): AdminUserAssets = {
    val bs = getBusinessesInternal
    val menus: Set[Menu] = bs.businesses.flatMap(b => Menu.getRecordsByBusinessId(b.id))
    val dishes = getDishes.dishes
    AdminUserAssets(businesses = bs.businesses, menus = menus, dishes = dishes)
  }
}
case class AdminUserAssets(businesses: Set[Business], couriers: Set[UserJson] = Set(),
                           dishes: Set[Dish] = Set(), menus: Set[Menu] = Set(), owners: Set[UserJson] = Set(), terminals: Set[UserJson] = Set())


class AdminUserAssetsActor extends Actor with ActorLogging {
  import AdminUserAssets._
  override def receive = {
    case GetMenus(creds) =>
      sender ! getMenus(creds)
    case GetDishes(creds) =>
      sender ! getDishes(creds)
    case GetCouriers(creds) =>
      sender ! getCouriers(creds)
    case GetOwners(creds) =>
      sender ! getOwners(creds)
    case GetChefs(creds) =>
      sender ! getChefs(creds)
    case GetBusinesses(creds) =>
      sender ! getBusinesses(creds)
  }
}
object AdminUserAssetsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport{
  import info.whereismyfood.modules.business.BusinessJsonSupport._
  import info.whereismyfood.modules.menu.DishJsonSupport._
  import info.whereismyfood.modules.menu.MenuJsonSupport._
  import info.whereismyfood.modules.user.UserJsonSupport._
  implicit val adminUserAssetsFormatter =
    jsonFormat(AdminUserAssets.apply, "businesses", "couriers", "dishes", "menus", "owners", "terminals")
}


