package info.whereismyfood.modules.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */

object ManagerUser extends GenericUserTrait[ManagerUser]{
  override def role: RoleID = Roles.manager
  def jobInBusiness: Business.JobInBusiness = Business.Jobs.owners
  override def of(creds: Creds) = ManagerUser(creds)
  def find(creds: Creds): Option[ManagerUser] = {
    find(creds.phone) match {
      case Some(account) =>
        account.creds.otp = creds.otp
        Some(account)
      case _ => None
    }
  }
  override protected def userActorFactory = None
}

final case class ManagerUser(private val creds: Creds)
  extends GenericUser(creds){
  override def compobj = ManagerUser
  //def jobInBusiness: Business.JobInBusiness = ManagerUser.jobInBusiness

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}
