package info.whereismyfood.modules.user

import scala.util.Try

/**
  * Created by zakgoichman on 11/21/16.
  */

object Roles {
  type RoleID = Long
  private def role(index: Long): Long = 1L << index

  val unknown = 0
  val client = role(0)
  val courier = role(1)
  val chef = role(2) | api.order.markReady | api.order.view
  val manager = role(3)
  object api{
    val master = MAX - 1 // good for 31 roles
    object order{
      val add = role(4)
      val modify = role(5)
      val delete = role(6)
      val markReady = role(7)
      val view = role(8)
      val all = add | modify | delete | markReady | view
    }
    object business{
      val info = role(9)
      val apier_list = role(10)
      val courier_list = role(11)
      val chef_list = role(12)
      val owner_list = role(13)
      val all = info | owner_list | courier_list | chef_list | apier_list
    }
  }
  val MAX = role(62) - 1L


  def apply(str: String): RoleID = str.toLong
  def apply(strs: Seq[String]): RoleID = strs map(_.toLong) reduce(_ | _)

  def isManager(role: RoleID):Boolean = {
    (role & manager) != 0
  }
  def isChef(role: RoleID):Boolean = {
    (role & chef) != 0
  }
  def isCourier(role: RoleID):Boolean = {
    (role & courier) != 0
  }
  def isClient(role: RoleID):Boolean = {
    (role & client) != 0
  }

  def isMaster(role: RoleID) = role == MAX

  def isauthorized(role: RoleID)(implicit creds: Creds): Boolean = {
    (creds.role & role) != 0
  }

  def isauthorized(role: RoleID, businessId: Long*)(implicit creds: Creds): Boolean ={
    Try{
      (creds.role & role) != 0 &&
        (creds.role == Roles.api.master ||
          creds.businessIds.intersect(businessId.toSet).nonEmpty)
    }.getOrElse(false)
  }
}
