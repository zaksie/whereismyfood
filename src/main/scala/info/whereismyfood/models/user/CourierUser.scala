package info.whereismyfood.models.user

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key}
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.business.Business
import info.whereismyfood.models.order.ProcessedOrderJsonSupport._
import info.whereismyfood.models.user.Roles.RoleID
import info.whereismyfood.models.vehicle.VehicleTypes
import info.whereismyfood.models.vehicle.VehicleTypes.VehicleType
import info.whereismyfood.modules.userActors.CourierUserActor
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
/**
  * Created by zakgoichman on 11/11/16.
  */

object CourierUser extends GenericUserTrait[CourierUser]{
  override def role: RoleID = Roles.courier
  def jobInBusiness: Business.JobInBusiness = Business._couriers
  def of(creds: Creds): CourierUser = CourierUser(creds)
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
    val keys = ids.toSeq.map(id=>datastore.newKeyFactory().setKind(USER_KIND).newKey(id))
    datastore.get(keys:_*).asScala.toSeq.flatMap(x=>CourierUser.of(x))
  }
  override protected def userActorFactory = Some(CourierUserActor)
}

final case class CourierUser(private val creds: Creds) extends GenericUser(creds){
  def toCourierJson: CourierJson = CourierJson(name, phone, image, vehicleType)

  def vehicleType = creds.vehicleType
  override def getOTPBody(code: String*): String = ???

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}
  override def extendFromDatastore(entity: Entity): this.type = this
}

final case class CourierJson(name: Option[String], phone: String, image: Option[String], vehicleType: Option[VehicleType] = Some(VehicleTypes.L))

object CourierJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val courierJsonFormatter = jsonFormat(CourierJson.apply, "name", "phone", "image", "vehicleType")
}
