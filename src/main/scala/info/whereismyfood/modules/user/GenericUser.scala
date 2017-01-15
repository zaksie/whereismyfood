package info.whereismyfood.modules.user

import java.time.{ZoneOffset, ZonedDateTime}

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import com.google.cloud.datastore._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.aux.MyConfig.Vars
import info.whereismyfood.libs.auth.{OTP}
import info.whereismyfood.libs.database.{Databases, DatastoreStorable}
import info.whereismyfood.modules.auth.RequestOTP
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.business.Business.JobInBusiness
import info.whereismyfood.modules.geo
import info.whereismyfood.modules.geo.{Address, Distance, Geolocation}
import info.whereismyfood.modules.user.Roles.RoleID
import info.whereismyfood.routes.auth.Login
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.util.Try
/**
  * Created by zakgoichman on 11/20/16.
  */
private object FieldNames {
  val _phone = "phone"
  val _deviceId = "deviceId"
  val _name = "name"
  val _email = "email"
  val _role = "role"
  val _address = "address"
  val _image = "image"
  val _verified = "verified"
  val _vehicleType = "vehicleType"
}

trait HasPropsFunc[T <: GenericUser] {
  def props(implicit user: T): Props
}

trait GenericUserTrait[T <: GenericUser]{
  def requestOTP(phone: String): Boolean = {
    find(phone) match {
      case Some(user) =>
        user.requestOTP()
      case _ => false
    }
  }

  def verifyOTP(creds: Creds): Option[T] = {
    find(creds.phone) match {
      case Some(user) =>
        if(user.verifyOTP(creds.otp.getOrElse(""))) Some(user)
        else None
      case _ => None
    }
  }

  UserRouter.addUserCompanionObject(this)
  def unlazy = None

  protected def userActorFactory: Option[HasPropsFunc[T]]
  def createWebSocketActor(implicit creds: Creds): Option[ActorRef] = {
    userActorFactory match {
      case Some(factory) =>
        import info.whereismyfood.aux.ActorSystemContainer.Implicits._
        find(creds.phone) match {
          case Some(user) =>
            Some(system.actorOf(factory.props(user)))
          case _ => None
        }
      case _ =>
        None
    }
  }

  protected def datastore = Databases.persistent.client
  protected val log = LoggerFactory.getLogger("GenericUserTrait")
  val kind: String = "User"

  def role: RoleID
  def jobInBusiness: JobInBusiness

  def isAuthorized(user: GenericUser): Boolean = isAuthorized(user.role)
  def isAuthorized(roleId: RoleID): Boolean = {
    (roleId & role) != 0
  }

  def handshake(creds: Creds): ToResponseMarshallable = {
    isAuthorized(creds.role) match {
      case true =>
        find(creds.phone) match {
          case Some(user) if user.role == creds.role => 200
          case Some(user) => HttpResponse(status = 205, entity = user.jwt)
          case _ => 403
        }
      case _ => 403
    }
  }

  def getAllNear(here: geo.LatLng, ids: Set[String]): Seq[CourierUser] = {
    val recent = ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(Vars.recent_minutes).toEpochSecond
    val radius = MyConfig.Vars.nearby_meter
    Geolocation.retrieve(jobInBusiness, ids.toSeq:_*)
        .filter(x => Distance.asTheCrowFlies(x.coords.toLatLng, here) < radius)
        .filter(_.timestamp > recent).flatMap { x =>
      Try {
        CourierUser.find(x.key).get.setGeolocation(x)
      }.toOption
    }

    //TODO: remove. here just for testing
    Seq(CourierUser.find("world").get)
  }

  def find(phone: String): Option[T] = {
    getFromDatastore(phone) match {
      case Some(account) if isAuthorized(account) =>
        Some(account)
      case _ => None
    }
  }
  def findAndVerify(apiKey: APIKey): Option[T] = {
    find(apiKey.fkey) match {
      case Some(user) =>
        user.deviceId match {
          case Some(existingDevId) if existingDevId.nonEmpty =>
            if (existingDevId == apiKey.uuid) Some(user)
            else None
          case _ =>
            user.verify(apiKey)
            Option(user.save)
        }
      case _ => None
    }
  }

  def of(entity: Entity): Option[T] = {
    import FieldNames._
    Try {
      val creds = Creds(entity.getString(_phone),
        Option(entity.getString(_deviceId)),
        None,
        Option(entity.getString(_name)),
        Option(entity.getString(_email)),
        None)

      creds.setVerified(entity.getBoolean(_verified))
      creds.setImage(Option(entity.getString(_image)))
      Try {
        creds.setVehicleType(Option(entity.getString(_vehicleType)))
      }
      creds.setAddress{
        Try{
          new Address(entity.getEntity(_address))
        }.getOrElse(Address.empty)
      }

      creds.setRole(entity.getLong(_role))
      creds.setBusinesses(Business.getIdsFor(creds.phone, jobInBusiness))
      log.info(s"Got business list for $jobInBusiness: ${creds.businessIds.mkString}")
      val obj: T = of(creds)
      obj.extendFromDatastore(entity)
    }.toOption
  }

  def of(creds: Creds): T

  def getFromDatastore(phone: String): Option[T] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey(phone)
    Try {
      of {
        datastore.get(key, ReadOption.eventualConsistency)
      }.get
    }.toOption
  }

  def saveOTP(creds: Creds): Boolean ={
    getFromDatastore(creds.phone) match {
      case Some(account) if creds.otp.isDefined =>
        OTP.save(account.otpKey, creds.otp.get)
        true
      case _ => false
    }
  }
}

object GenericUser{
  val USER_KIND = "User"
  protected val log = LoggerFactory.getLogger("GenericUser")

  def getById(userId: String): Option[Creds] = {
    try {
      val client = Databases.persistent.client
      val key = client.newKeyFactory().setKind(GenericUser.USER_KIND).newKey(userId)
      val entity = Databases.persistent.client.get(key, ReadOption.eventualConsistency())
      import FieldNames._
      val c = Creds(userId,
        Some(entity.getString(_deviceId)),
        None,
        Some(entity.getString(_name)),
        Some(entity.getString(_email)),
        None
      )
          .setImage(Some(entity.getString(_image)))
          .setRole(entity.getLong(_role))
      Some(c)
    } catch {
      case e: Throwable =>
        log.error("Error in getById", e)
        None
    }
  }
}
abstract class GenericUser(private val creds: Creds)
  extends DatastoreStorable {
  import GenericUser._
  protected var __geolocation: Option[Geolocation] = None
  def geolocation: Option[Geolocation] = __geolocation

  protected lazy val addressOptionProcessed = {
    if(creds.geoaddress.isDefined) creds.geoaddress
    else Address.of(creds.address)
  }
  def jwt = Login.createToken(this)

  def compobj: GenericUserTrait[_]
  def jobInBusiness: Business.JobInBusiness = compobj.jobInBusiness
  def address = addressOptionProcessed
  def phone: String = creds.phone
  def name: Option[String] = creds.name
  def email: Option[String] = creds.email
  def deviceId: Option[String] = creds.deviceId
  def role: RoleID = if(creds.role != Roles.unknown) creds.role else compobj.role
  def businessIds: Set[Long] = creds.businessIds
  def verified: Boolean = creds.verified
  def image: Option[String] = creds.image
  def vehicleType: Option[String] = creds.vehicleType

  def verify(apiKey: APIKey): this.type = {
    creds.setDeviceIdIfNone(apiKey.uuid)
    creds.setVerified(true)
    this
  }

  def toCreds(otp: Option[String] = None) : Creds ={
    creds.copy(otp = otp)
  }

  def save: this.type ={
    saveToDatastore()
    this
  }


  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(USER_KIND).newKey(phone)

    import FieldNames._
    val entity = FullEntity.newBuilder(key)
        .set(_deviceId, deviceId.getOrElse(""))
        .set(_phone, phone)
        .set(_email, email.getOrElse(""))
        .set(_name, name.getOrElse(""))
        .set(_image, image.getOrElse(""))
        .set(_role, role)
        .set(_verified, verified)
        .set(_vehicleType, vehicleType.getOrElse(""))

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

  def otpKey: String = Seq(role, phone).mkString("-") + "-OTP"

  def requestOTP(): Boolean = {
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
  def verifyOTP: Boolean = {
    verifyOTP(creds.otp.getOrElse(""))
  }
  def verifyOTP(_otp: String): Boolean = {
    if (_otp == "") return false

    val o = OTP.retrieve(otpKey)
    o match {
      case otp @ Some(_) if otp.get == _otp =>
        true
      case Some(otp) =>
        println("incorrect otp. should be " + otp)
        false
      case _ => false
    }
  }
  def extendFromDatastore(entity: Entity): this.type

  def asDatastoreAncestor: PathElement = PathElement.of(USER_KIND, phone)

  def setGeolocation(geolocation: Geolocation): this.type = {__geolocation = Option(geolocation); this}
}
