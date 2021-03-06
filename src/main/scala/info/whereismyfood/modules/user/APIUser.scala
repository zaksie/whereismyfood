package info.whereismyfood.modules.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */

object APIUser extends GenericUserTrait[APIUser]{
  override def role: RoleID = Roles.api.order.all
  override def of(creds: Creds) = APIUser(creds)
  override def isAuthorized(user: GenericUser): Boolean = {
    (user.role & role) != 0
  }
  def jobInBusiness: Business.JobInBusiness = Business.DSTypes.apiers
  override protected def userActorFactory = None
}

final case class APIUser(override val creds: Creds) extends GenericUser(creds){
  override def compobj = APIUser
  override def _copy = copy _
  override def getOTPBody(code: String*): String = {
    s"Yummlet code for API user: $code"
  }

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}
