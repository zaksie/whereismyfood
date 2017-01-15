package info.whereismyfood.modules.user

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.menu.{Dish, Menu}
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/16/16.
  */

object AdminUserAssets {
  def getAllFor(creds: Creds): Option[AdminUserAssets] = {
    if (creds.phone.isEmpty) return None

    val businesses = Business.getAllFor(creds.phone, Business.DSTypes.owners)
    val couriers = CourierUser.getById(businesses.flatMap(_.couriers).toSeq:_*).map(_.toCourierJson).toSet
    val dishes: Set[Dish] = businesses.flatMap(b => Dish.getRecordsByBusinessId(b.id))
    val menus: Set[Menu] = businesses.flatMap(b => Menu.getRecordsByBusinessId(b.id))

    Option {
      AdminUserAssets(businesses, couriers, dishes, menus)
    }
  }
}
case class AdminUserAssets(businesses: Set[Business], couriers: Set[CourierJson], dishes: Set[Dish], menus: Set[Menu])

object AdminUserAssetsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport{
  import info.whereismyfood.modules.business.BusinessJsonSupport._
  import info.whereismyfood.modules.menu.DishJsonSupport._
  import info.whereismyfood.modules.menu.MenuJsonSupport._
  import info.whereismyfood.modules.user.CourierJsonSupport._
  implicit val adminUserAssetsFormatter = jsonFormat(AdminUserAssets.apply, "businesses", "couriers", "dishes", "menus")
}


