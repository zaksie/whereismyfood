package info.whereismyfood.modules.user

import akka.actor.{Actor, Props}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID


/**
  * Created by zakgoichman on 10/24/16.
  */

object CourierModule {
  def props = Props[CourierActor]
}

class CourierActor extends UserActor {
  override protected val job = Business.DSTypes.couriers
  override protected val role: RoleID = Roles.courier
  override protected def updateOrCreateInDB = CourierUser.of

  override def receive: Receive = {
    case AddUser(userJson) =>
      sender ! updateOrCreateUser(userJson)
  }
}



