package info.whereismyfood.modules.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

/**
  * Created by zakgoichman on 11/18/16.
  */


object ClientUser extends GenericUserTrait[ClientUser]{
  def getOrCreate(token: String): ClientUser = {
    ClientUser.getFromDatastore(token) match {
      case Some(user) => user
      case _ => ClientUser.of(token).save
    }
  }

  override def role: RoleID = Roles.client
  def jobInBusiness: Business.JobInBusiness = Business.Jobs.clients
  override def of(creds: Creds) = ClientUser(creds)
  override protected def userActorFactory = Some(ClientUserActor)
  override def requestOTP(phone: String): Boolean = {
    of(phone).requestOTP()
  }
  def of(phone: String): ClientUser = of(Creds(phone))
  override def verifyOTP(creds: Creds): Option[ClientUser] = {
    val user = of(creds)
    user.verifyOTP match {
      case true =>
        find(user.phone) match {
          case existingUser@Some(_) =>
            existingUser
          case _ =>
            user.creds.setVerified(true)
            Some(user.save)
        }
      case _ => None
    }
  }
}

final case class ClientUser(private val creds: Creds)
  extends GenericUser(creds){
  override def compobj = ClientUser
  //def jobInBusiness: Business.JobInBusiness = ClientUser.jobInBusiness

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this
}