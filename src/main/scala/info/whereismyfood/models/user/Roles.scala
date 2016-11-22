package info.whereismyfood.models.user

import scala.util.Try

/**
  * Created by zakgoichman on 11/21/16.
  */

object Roles {

  type RoleID = Long
  private def role(index: Long): Long = 1 << index

  val unknown = 0
  val client = role(0)
  val courier = role(1)
  val chef = role(2) | api.order.modify
  val manager = role(3)
  object api{
    val master = MAX - 1 // good for 31 roles
    object order{
      val add = role(4)
      val modify = role(5)
      val delete = role(6)
      val markReady = role(7)
      val all = add | modify | delete | markReady
    }
  }
  val MAX = 1 << 32


  def apply(str: String): RoleID = str.toLong
  def apply(strs: Seq[String]): RoleID = strs map(_.toLong) reduce(_ | _)

  def isManager(role: Long):Boolean = {
    (role & manager) != 0
  }
  def isChef(role: Long):Boolean = {
    (role & chef) != 0
  }
  def isCourier(role: Long):Boolean = {
    (role & courier) != 0
  }
  def isClient(role: Long):Boolean = {
    (role & client) != 0
  }

  def isauthorized(role: RoleID)(implicit user: GenericUser): Boolean = {
    (user.role & role) != 0
  }

  def isauthorized(role: RoleID, businessId: Long)(implicit user: GenericUser): Boolean ={
    Try{
      (user.role & role) != 0 &&
        (user.role == Roles.api.master ||
          user.businessIds.contains(businessId))
    }.getOrElse(false)
  }
}
