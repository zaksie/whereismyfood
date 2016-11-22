package info.whereismyfood.models.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */


object ClientUser extends GenericUserTrait[ClientUser]{
  override def role: RoleID = Roles.client
  override def of(creds: Creds): ClientUser = ClientUser(creds)
}

final case class ClientUser(private val creds: Creds)
  extends GenericUser(creds){

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}