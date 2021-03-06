package info.whereismyfood.modules.user

import akka.actor.{Actor, Props}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

/**
  * Created by zakgoichman on 10/24/16.
  */

object ManagerModule {
  def props = Props[ManagerInfoActor]
}

class ManagerInfoActor extends UserInfoActor {
  override protected val job = Business.DSTypes.owners
  override protected val role: RoleID = Roles.manager
  override protected def updateOrCreateInDB = ManagerUser.of

  override def receive: Receive = {
    case AddUser(userJson) =>
      sender ! updateOrCreateUser(userJson)
  }
}



