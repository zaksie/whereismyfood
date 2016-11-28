package info.whereismyfood.modules.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */


object ClientUser extends GenericUserTrait[ClientUser]{
  override def role: RoleID = Roles.client
  def jobInBusiness: Business.JobInBusiness = Business.Jobs.none
  override def of(creds: Creds) = ClientUser(creds)
  override protected def userActorFactory = Some(ClientUserActor)
}

final case class ClientUser(private val creds: Creds)
  extends GenericUser(creds){
  def jobInBusiness: Business.JobInBusiness = ClientUser.jobInBusiness

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}