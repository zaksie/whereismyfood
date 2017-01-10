package info.whereismyfood.modules.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.user.{CourierJson, CourierUser, Creds}
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/16/16.
  */

object AdminUserAssets {
  def getAllFor(creds: Creds): Option[AdminUserAssets] = {
    if (creds.phone.isEmpty) return None

    val businesses = Business.getAllFor(creds.phone, Business.DSTypes.owners)
    val ids: Set[String] = businesses.flatMap(_.couriers)
    val couriers = CourierUser.getIdsFromDB(ids).map(_.toCourierJson).toSet

    Option {
      AdminUserAssets(businesses, couriers)
    }
  }
}
case class AdminUserAssets(businesses: Set[Business], couriers: Set[CourierJson])

object AdminUserAssetsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport{
  import BusinessJsonSupport._
  import info.whereismyfood.modules.user.CourierJsonSupport._
  implicit val adminUserAssetsFormatter = jsonFormat(AdminUserAssets.apply, "businesses", "couriers")
}


