package info.whereismyfood.modules.user

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.courier.VehicleTypes
import info.whereismyfood.modules.courier.VehicleTypes.VehicleType
import info.whereismyfood.modules.user.Roles.RoleID
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
/**
  * Created by zakgoichman on 11/11/16.
  */

object CourierUser extends GenericUserTrait[CourierUser]{
  override def role: RoleID = Roles.courier
  def jobInBusiness: Business.JobInBusiness = Business.Jobs.couriers
  override def of(creds: Creds) = CourierUser(creds)
  def of(courier: CourierJson, businessId: Long): CourierUser = {
    find(courier.phone) match {
      case Some(user) =>
        user.creds.addBusiness(businessId).addRole(role)
        user
      case _ =>
        CourierUser {
          Creds(courier.phone, None, None, courier.name, None, None)
            .setImage(courier.image)
            .setVehicleType(courier.vehicleType)
            .setBusinesses(Set(businessId))
            .setRole(role)
        }
    }
  }
  def getIdsFromDB(ids: Set[String]): Seq[CourierUser] = {
    val keys = ids.toSeq.map(id=>datastore.newKeyFactory().setKind(kind).newKey(id))
    datastore.get(keys:_*).asScala.toSeq.flatMap(x=>CourierUser.of(x))
  }
  override protected def userActorFactory = Some(CourierUserActor)
}

final case class CourierUser(private val creds: Creds) extends GenericUser(creds){
  override def compobj = CourierUser
  //def jobInBusiness: Business.JobInBusiness = CourierUser.jobInBusiness
  def toCourierJson: CourierJson = CourierJson(name, phone, image, vehicleType)
  def toCourierJsonOption: Option[CourierJson] = Some(toCourierJson)

  override def getOTPBody(code: String*): String = ???

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}
  override def extendFromDatastore(entity: Entity): this.type = this
}

final case class CourierJson(name: Option[String], phone: String, image: Option[String], vehicleType: Option[VehicleType] = Some(VehicleTypes.default))

object CourierJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val courierJsonFormatter = jsonFormat(CourierJson.apply, "name", "phone", "image", "vehicleType")
}
