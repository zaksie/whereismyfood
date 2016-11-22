package info.whereismyfood.models.user

import com.google.cloud.datastore._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.libs.auth.{OTP, VerificationResult}
import info.whereismyfood.libs.database.{Databases, DatastoreStorable}
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.business.Business
import info.whereismyfood.models.user.Roles.RoleID
import info.whereismyfood.routes.auth.Login
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.collection.JavaConverters._
import scala.concurrent.Await
import info.whereismyfood.modules.auth.RequestOTP
/**
  * Created by zakgoichman on 11/20/16.
  */
private object FieldNames {
  val _phone = "phone"
  val _uuid = "deviceId"
  val _name = "name"
  val _email = "email"
  val _role = "role"
  val _address = "address"
  val _image = "image"
  val _verified = "verified"
}

object UserRouter{
  var classes = Set[Class[_]]()
  def of(role: RoleID, creds:Creds, businessIds: Set[String] = Set(), address: Option[Address] = None): Unit ={
  }
}
trait GenericUserTrait[T <: GenericUser]{
  def datastore = Databases.persistent.client
  def role: RoleID
  val log = LoggerFactory.getLogger("GenericUser")
  val USER_KIND = "User"

  def isAuthorized(user: GenericUser): Boolean = {
    (user.role & role) != 0
  }

  def find(phone: String): Option[T] = {
    getFromDatastore(phone) match {
      case Some(account) if isAuthorized(account) =>
        Some(account)
      case _ => None
    }
  }
  def findAndVerify(apiKey: APIKey): Option[T] = {
    find(apiKey.key) match {
      case Some(user) =>
        if(user.deviceId.isEmpty){
          user.setDeviceId(apiKey.uuid)
          user.save
        }
        Some(user)
      case _ => None
    }
  }
  def of(entity: Entity): Option[T] = {
    import FieldNames._
    Try {
      val creds = Creds(entity.getString(_phone),
        Option(entity.getString(_uuid)),
        None,
        Option(entity.getString(_name)),
        Option(entity.getString(_email)),
        None)

      creds.setVerified(entity.getBoolean(_verified))
      creds.setImage(Option(entity.getString(_image)))

      creds.setAddress{
        Try{
          new Address(entity.getEntity(_address))
        }.getOrElse(Address.empty)
      }

      creds.setRole(entity.getLong(_role))
      creds.setBusinesses(Business.getIdsFor(creds.phone))
      val obj: T = of(creds)
      obj.extendFromDatastore(entity)
    }.toOption
  }

  def of(creds: Creds): T

  def getFromDatastore(phone: String): Option[T] = {
    val key = datastore.newKeyFactory().setKind(USER_KIND).newKey(phone)
    Try {
      of {
        datastore.get(key, ReadOption.eventualConsistency)
      }.get
    }.toOption
  }

  // OTP is only for registered users
  def saveOTP(creds: Creds): Boolean ={
    getFromDatastore(creds.phone) match {
      case Some(account) if creds.otp.isDefined =>
        OTP.save(account.otpKey, creds.otp.get)
        true
      case _ => false
    }
  }

  def getBusinesses(phone: String): Set[Long] = {
    Business.getAllFor(phone).map(_.id)
  }
}

abstract class GenericUser(private val creds: Creds)
  extends DatastoreStorable {

  val USER_KIND = "User"

  lazy val addressOptionProcessed = {
    if(creds.address.isDefined) creds.address
    else Address.of(creds.addressString)
  }
  def jwt = Login.createToken(this)

  def address = addressOptionProcessed
  def phone: String = creds.phone
  def name: Option[String] = creds.name
  def email: Option[String] = creds.email
  def deviceId: Option[String] = creds.deviceId
  def role: RoleID = creds.role
  def businessIds: Set[Long] = creds.businessIds
  def verified: Boolean = creds.verified
  def image: Option[String] = creds.image

  def setDeviceId(deviceId: String): Unit = creds.setDeviceId(deviceId)

  def toCreds(otp: Option[String] = None) : Creds ={
    creds.copy(otp = otp)
  }

  def save: this.type ={
    saveToDatastore
    this
  }

  def verifyOTP: Boolean = {
    if (creds.otp.isEmpty || creds.otp.get == "") return false

    OTP.retrieve(otpKey) match {
      case otp @ Some(_) if otp == creds.otp =>
        true
      case Some(otp) =>
        println("incorrect otp. should be " + otp)
        false
      case _ => false
    }
  }

  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(USER_KIND).newKey(phone)

    import FieldNames._
    val entity = FullEntity.newBuilder(key)
      .set(_uuid, deviceId.getOrElse(""))
      .set(_phone, phone)
      .set(_email, email.getOrElse(""))
      .set(_name, name.getOrElse(""))
      .set(_image, image.getOrElse(""))
      .set(_role, role)
      .set(_verified, verified)

    val addr = address match {
      case Some(a) => a
      case _ => Address.empty
    }
    entity.set(_address, addr.asDatastoreEntity.get)

    extendDatastoreEntity(entity)
    Option(entity.build)
  }

  def extendDatastoreEntity(entity: FullEntity.Builder[Key]): Unit

  def getOTPBody(otps: String*): String = {
    s"Your Yummlet.com code: ${otps.head}"
  }

  def otpKey = (Seq(role, phone, deviceId.getOrElse("")) ++ businessIds).mkString("-") + "-OTP"

  def requestOTP: Boolean = {
    Try {
      val otps = generateOTPs()
      saveOTPs(otps.toSeq: _*)
      val actorRef = Await.result(system.actorSelection("/user/modules/request-verify-phone").resolveOne(), resolveTimeout.duration)
      actorRef ! RequestOTP(phone, getOTPBody(otps.toSeq: _*))
    }.isSuccess
  }

  def generateOTPs(): Set[String] = {
    import info.whereismyfood.libs.math.Misc._
    Set(generateNumericCode(4))
  }

  def saveOTPs(otps: String*): Unit = {
    otps foreach { otp =>
      OTP.save(otpKey, otp)
    }
  }

  def verifyOTP(user: GenericUser, otpFromUser: String): VerificationResult = {
    OTP.retrieve(otpKey) match{
      case Some(otpFromDB) if otpFromDB == otpFromUser =>
        VerificationResult(Some(this))
      case None => VerificationResult(None)
    }
  }

  def extendFromDatastore(entity: Entity): this.type

  def asDatastoreAncestor: PathElement = PathElement.of(USER_KIND, phone)
}