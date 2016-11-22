package info.whereismyfood.models.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */


object ChefUser extends GenericUserTrait[ChefUser]{
  override def role: RoleID = Roles.chef

  override def of(creds: Creds) = ChefUser(creds)

}

final case class ChefUser(private val creds: Creds)
  extends GenericUser(creds){

  override def getOTPBody(code: String*): String = ???

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}