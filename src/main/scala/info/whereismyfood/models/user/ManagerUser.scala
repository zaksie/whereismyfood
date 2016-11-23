package info.whereismyfood.models.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.models.business.Business
import info.whereismyfood.models.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */

object ManagerUser extends GenericUserTrait[ManagerUser]{
  override def role: RoleID = Roles.manager
  def jobInBusiness: Business.JobInBusiness = Business._owners

  override def of(creds: Creds) = ManagerUser(creds)
  def find(creds: Creds): Option[ManagerUser] = {
    find(creds.phone) match {
      case Some(account) =>
        account.creds.otp = creds.otp
        Some(account)
      case _ => None
    }
  }
}

final case class ManagerUser(private val creds: Creds)
  extends GenericUser(creds){

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}
