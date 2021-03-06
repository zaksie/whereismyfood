package info.whereismyfood.modules.user

import java.time.{ZoneOffset, ZonedDateTime}

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import com.google.cloud.datastore._
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.aux.MyConfig.Vars
import info.whereismyfood.libs.auth.OTP
import info.whereismyfood.libs.database.{Databases, DatastoreStorable}
import info.whereismyfood.modules.auth.RequestOTP
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.business.Business.JobInBusiness
import VehicleTypes.VehicleType
import info.whereismyfood.modules.geo
import info.whereismyfood.modules.geo.{Address, Distance, Geolocation}
import info.whereismyfood.modules.user.CourierUser.find
import info.whereismyfood.modules.user.Roles.RoleID
import info.whereismyfood.routes.auth.Login
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import collection.JavaConverters._

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
  val _ref = "ref"
}

trait HasPropsFunc[T <: GenericUser] {
  def props(implicit user: T): Props
}

object transactionalUser{
  val prefix = "transactional-user/"
  val expiry: Duration = 2 days
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

  def getById(ids: String*): Seq[T] = {
    val keys = ids.map(id=>datastore.newKeyFactory().setKind(kind).newKey(id))
    datastore.get(keys:_*).asScala.toSeq.flatMap(x => of(x))
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

  def of(user: UserJson, role: RoleID): Option[T] = {
    find(user.prevPhone.getOrElse("")) match {
      case Some(user_tmp) =>
        val email = if(user.email.isEmpty) user_tmp.email else Option(user.email)
        val creds_new = user_tmp.creds.copy(phone = user.phone, name = Option(user.name), email = email)
            .setImage(Try(user.image.getOrElse(user_tmp.image.get)).toOption)
            .setVehicleType(Try(user.vehicleType.getOrElse(user_tmp.vehicleType.get)).toOption)
            .setRole(user_tmp.role | role)
            .setVerified(user_tmp.verified)
        if(user.prevPhone != Some(user.phone)) {
          find(user.phone) match {
            case Some(_) => //Phone number is already taken by another user. signal error
              return None
            case _ if !user_tmp.removeFromDatastore()=>
              return None
            case _ =>
          }
        }

        Option(user_tmp._copy(creds_new).asInstanceOf[T].save)
      case _ =>
        Option(of{
          Creds(user.phone, None, None, Option(user.name), Option(user.email), None)
              .setImage(user.image)
              .setVehicleType(user.vehicleType)
              .setRole(role)
              .setRef()
        }.save)
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
      Try(entity.getString(_ref)).toOption match {
        case Some(ref) =>
          creds.setRef(ref)
        case _ =>
          creds.setRef()
      }
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
abstract class GenericUser(val creds: Creds)
  extends DatastoreStorable {
  import GenericUser._
  protected var __geolocation: Option[Geolocation] = None
  def geolocation: Option[Geolocation] = __geolocation

  protected lazy val addressOptionProcessed = creds.geoaddress
  def jwt = Login.createTokenFromUser(this)

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
  def ref: String = {
    if(creds.ref.isEmpty) {
      creds.setRef()
      creds.ref.get
    } else creds.ref.get
  }

  def verify(apiKey: APIKey): this.type = {
    creds.setDeviceIdIfNone(apiKey.uuid)
    creds.setVerified(true)
    this
  }

  def _copy: Creds => _ <: GenericUser

  def toCreds(otp: Option[String] = None) : Creds ={
    creds.deepCopy.setOtp(otp)
  }

  def save: this.type ={
    try {
      saveToDatastore()
    }catch{
      case e: Throwable =>
        log.error("Failed to save to datastore", e)
    }
    this
  }

  def toJson(businessIds: Set[Long], expiry: Option[Long] = None): UserJson =
    UserJson(name = name.getOrElse(""),
      phone = phone,
      email = email.getOrElse(""),
      image = image,
      vehicleType = vehicleType,
      businessIds = this.businessIds.intersect(businessIds),
      expiresInMillis = expiry
    )

  override def getDatastoreKey: Option[Key] = Option(datastore.newKeyFactory().setKind(USER_KIND).newKey(phone))

  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    asDatastoreEntityUnbuilt match {
      case Some(entity) =>
        Option(entity.build)
      case _ =>
        None
    }
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
  def asDatastoreEntityUnbuilt: Option[FullEntity.Builder[_ <: IncompleteKey]] = {
    import FieldNames._
    Try {
      val entity = FullEntity.newBuilder(getDatastoreKey.get)
          .set(_deviceId, deviceId.getOrElse(""))
          .set(_phone, phone)
          .set(_email, email.getOrElse(""))
          .set(_name, name.getOrElse(""))
          .set(_image, image.getOrElse(""))
          .set(_role, role)
          .set(_verified, verified)
          .set(_vehicleType, vehicleType.getOrElse(""))
          .set(_ref, ref)

      val addr = address match {
        case Some(a) => a
        case _ => Address.empty
      }
      entity.set(_address, addr.asDatastoreEntity.get)

      extendDatastoreEntity(entity)
      entity
    }.toOption
  }

  def setGeolocation(geolocation: Geolocation): this.type = {__geolocation = Option(geolocation); this}
}

final case class UserJson(name: String, phone: String, email: String, image: Option[String],
                          vehicleType: Option[VehicleType] = Some(VehicleTypes.default),
                          businessIds: Set[Long], businessIdsToRemove: Set[Long] = Set(),
                          prevPhone: Option[String] = None, expiresInMillis: Option[Long] = None)

object UserJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val userJsonFormatter = jsonFormat(UserJson.apply, "name", "phone", "email", "image",
    "vehicleType", "businessIds", "businessIdsToRemove", "prevPhone", "expiresInMillis")
}