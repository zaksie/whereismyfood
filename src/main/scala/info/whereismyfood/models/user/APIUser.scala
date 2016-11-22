package info.whereismyfood.models.user

import com.google.cloud.datastore.{Entity, Key}
import com.google.cloud.datastore.FullEntity.Builder
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */

object APIUser extends GenericUserTrait[APIUser]{
  override def role: RoleID = Roles.api.order.all

  override def of(creds: Creds) = APIUser(creds)

  override def isAuthorized(user: GenericUser): Boolean = {
    (user.role & role) != 0
  }
}

final case class APIUser(private val creds: Creds) extends GenericUser(creds){

  override def getOTPBody(code: String*): String = {
    s"Yummlet code for API user: $code"
  }

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}
