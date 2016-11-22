package info.whereismyfood.models.user

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.user.Roles.RoleID
import info.whereismyfood.models.vehicle.VehicleTypes.VehicleType
import spray.json.DefaultJsonProtocol


/**
  * Created by zakgoichman on 11/2/16.
  */
final case class APIKey(key: String, uuid: String)
final case class Creds(phone: String, private var __deviceId: Option[String] = None, var otp: Option[String] = None,
                                name: Option[String] = None, email: Option[String] = None, addressString: Option[String] = None){

  def setDeviceId(deviceId: String) = __deviceId = Some(deviceId)
  def deviceId: Option[String] = __deviceId

  private var __role: RoleID = Roles.unknown
  def setRole(role: RoleID): Creds = {this.__role = role; this}
  def role: RoleID = __role

  private var __businessIds: Set[Long] = Set()
  def setBusinesses(businessIds: Set[Long]): Creds = {this.__businessIds = businessIds; this}
  def businessIds = __businessIds

  private var __address: Option[Address] = None
  def setAddress(address: Address): Creds = {this.__address = Option(address); this}
  def address = __address

  private var __verified: Boolean = false
  def setVerified(verified: Boolean): Creds = {this.__verified = verified; this}
  def verified = __verified


  private var __image: Option[String] = None
  def setImage(image: Option[String]): Creds = {this.__image = image;this}
  def image = __image

  private var __vehicleType: Option[VehicleType] = None
  def setVehicleType(vehicleType: Option[VehicleType]): Creds = {this.__vehicleType = vehicleType; this}
  def vehicleType = __vehicleType

  def addBusiness(businessId: RoleID) = {__businessIds += businessId; this}
  def removeBusiness(businessId: RoleID) = {__businessIds -= businessId; this}
  def addRole(role: RoleID) = {__role |= role; this}
  def removeRole(role: RoleID) = {__role &= Roles.MAX - role; this}
}

object CredsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val credsFormatter = jsonFormat(Creds, "phone", "deviceId", "otp", "name", "email", "address")
  implicit val apikeyFormatter = jsonFormat(APIKey, "key", "uuid")
}