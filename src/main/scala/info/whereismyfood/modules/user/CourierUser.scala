package info.whereismyfood.modules.user

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.util.Try
/**
  * Created by zakgoichman on 11/11/16.
  */

object CourierUser extends GenericUserTrait[CourierUser]{
  override def role: RoleID = Roles.courier
  def jobInBusiness: Business.JobInBusiness = Business.DSTypes.couriers
  override def of(creds: Creds) = CourierUser(creds)
  override protected def userActorFactory = Some(CourierUserActor)
}

final case class CourierUser(override val creds: Creds) extends GenericUser(creds){
  override def compobj = CourierUser
  //def jobInBusiness: Business.JobInBusiness = CourierUser.jobInBusiness
  override def _copy = copy _

  override def getOTPBody(code: String*): String = ???

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}
  override def extendFromDatastore(entity: Entity): this.type = this
}


