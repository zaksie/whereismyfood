package info.whereismyfood.modules.user

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.courier.VehicleTypes
import info.whereismyfood.modules.courier.VehicleTypes.VehicleType
import info.whereismyfood.modules.geo.Address
import info.whereismyfood.modules.user.Roles.RoleID
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol


/**
  * Created by zakgoichman on 11/2/16.
  */

private object CredsLogger{
  val log = LoggerFactory.getLogger("Creds")
}

final case class APIKey(key: String, uuid: String)
final case class Creds(phone: String, uuid: Option[String] = None, var otp: Option[String] = None,
                       name: Option[String] = None, email: Option[String] = None, address: Option[String] = None){

  private var __deviceId: Option[String] = None
  def setDeviceIdIfNone(deviceId: String) = __deviceId = Some(deviceId)
  def deviceId: Option[String] = uuid.orElse(__deviceId)

  private var __role: RoleID = Roles.unknown
  def setRole(role: RoleID): Creds = {this.__role = role; this}
  def role: RoleID = __role

  private var __businessIds: Set[Long] = Set()
  def setBusinesses(businessIds: Set[Long]): Creds = {this.__businessIds = businessIds; this}
  def businessIds = __businessIds

  private var __address: Option[Address] = None
  def setAddress(address: Address): Creds = {this.__address = Option(address); this}
  private def setAddress(address: Option[Address]): Option[Address] = {this.__address = address; address}
  def geoaddress: Option[Address] = {
    __address match {
      case addr @ Some(_) => addr
      case _ => setAddress(Address.of(address))
    }
  }

  private var __verified: Boolean = false
  def setVerified(verified: Boolean): Creds = {this.__verified = verified; this}
  def verified = __verified


  private var __image: Option[String] = None
  def setImage(image: Option[String]): Creds = {this.__image = image;this}
  def image = __image

  private var __vehicleType: Option[VehicleType] = Some(VehicleTypes.default)
  def setVehicleType(vehicleType: Option[VehicleType]): Creds = {
    this.__vehicleType = vehicleType
    this
  }
  def vehicleType = __vehicleType

  def addBusiness(businessId: RoleID) = {__businessIds += businessId; this}
  def removeBusiness(businessId: RoleID) = {__businessIds -= businessId; this}
  def addRole(role: RoleID) = {__role |= role; this}
  def removeRole(role: RoleID) = {__role &= Roles.MAX - role; this}
}

object CredsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val credsFormatter = jsonFormat(Creds, "phone", "uuid", "otp", "name", "email", "address")
  implicit val apiKeyFormatter = jsonFormat(APIKey, "key", "uuid")
}