package info.whereismyfood.models.business

import info.whereismyfood.models.user.{CourierJson, CourierUser, Creds}

/**
  * Created by zakgoichman on 11/16/16.
  */

object AdminUserAssets {
  def getAllFor(creds: Creds): Option[AdminUserAssets] = {
    if (creds.phone.isEmpty) return None

    val businesses = Business.getAllFor(creds.phone)
    val ids: Set[String] = businesses.flatMap(_.couriers)
    val couriers = CourierUser.getIdsFromDB(ids).map(_.toCourierJson).toSet

    Option {
      AdminUserAssets(businesses, couriers)
    }
  }
}
case class AdminUserAssets(businesses: Set[Business], couriers: Set[CourierJson])

object AdminUserAssetsJsonSupport extends BusinessJsonSupport {
  import info.whereismyfood.models.user.CourierJsonSupport._
  implicit val formatter1 = jsonFormat2(AdminUserAssets.apply)
}


