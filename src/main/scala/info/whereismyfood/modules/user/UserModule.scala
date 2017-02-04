package info.whereismyfood.modules.user

import akka.actor.{Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.business.Business.JobInBusiness
import info.whereismyfood.modules.user.Roles.RoleID


/**
  * Created by zakgoichman on 10/24/16.
  */

case class AddUser(user: UserJson){
  if(user.prevPhone.isEmpty || user.phone.isEmpty)
    throw new Exception("PrevPhone, Phone missing")
}

abstract class UserInfoActor extends Actor {
  protected val mediator = DistributedPubSub(context.system).mediator
  protected val job: JobInBusiness
  protected val role: RoleID

  protected def updateOrCreateInDB: (UserJson, RoleID) => Option[_ <: GenericUser]

  def updateUser(userJson: UserJson, roleID: RoleID = role): Boolean = {
    GenericUser.getById(userJson.phone) match {
      case Some(_) => updateOrCreateUser(userJson)
      case _ => false
    }
  }

  def updateOrCreateUser(userJson: UserJson, role: RoleID = role): Boolean = {
    def addRemoveBusinessIds(): Boolean = {
      userJson.businessIds.forall(businessId => Business.addJobTo(userJson.phone, businessId, job)) &&
          userJson.businessIdsToRemove.forall(businessId => Business.removeJobFrom(userJson.phone, businessId, job))
    }

    //save details only if new, and save list of businessIds for all.
    updateOrCreateInDB(userJson, role) match {
      case Some(_) => addRemoveBusinessIds()
      case _ => false
    }
  }
}



