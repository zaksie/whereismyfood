package info.whereismyfood.libs.auth

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Value, _}
import info.whereismyfood.libs.auth.DatabaseAccount.UUID
import info.whereismyfood.libs.auth.Roles.RoleID
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.routes.auth.JwtApi
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Created by zakgoichman on 11/2/16.
  */

object Roles {
  type RoleID = Long
  val client: RoleID = 1
  val courier: RoleID = 1 << 1
  val chef: RoleID = 1 << 2
  val manager: RoleID = 1 << 3
  val unknown: RoleID = 1 << 4

  def apply(str: String): Option[RoleID] = {
    val value = str.toLong
    if (value > manager || value < client)
      None
    else
      Some(value)
  }

  def apply(str: Seq[String]): Option[RoleID] = {
    val result: Seq[RoleID] = str.flatMap(x=>apply(x))
    if(result.length != str.length) None
    else Some(result.reduce(_ & _))
  }
}

final case class Creds(uuid: Option[UUID] = None, phone: String, dbid: Option[Long] = None, code: Option[String] = None, role: Option[RoleID] = None)

object CredsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val credsFormatter = jsonFormat(Creds, "uuid", "phone", "dbid", "code", "role")
}

object DatabaseAccount extends DatastoreFetchable[Seq[DatabaseAccount]]{
  val log = LoggerFactory.getLogger(DatabaseAccount.getClass)
  type UUID = String

  val kind = "Creds"
  val propkey_phone = "phone"
  val propkey_uuid = "uuid"
  val propkey_email = "email"
  val propkey_role = "role"
  val propkey_address = "address"

  def find(creds: Creds): Option[DatabaseAccount] = {
    if(creds.phone == "" || creds.uuid.isEmpty) None
    else getFromDatastore(creds.phone, creds.uuid) match {
      case Some(accounts) =>

        accounts.filter(_.uuid == creds.uuid.get) match {
          case Seq() => None
          case x if x.length > 1 =>
            log.error("Found more than one match for account (%s, %s)".format(creds.uuid.get, creds.phone))
            None
          case single => Some(single.head)
        }
      case _ => None
    }
  }

  def add(una: UnverifiedAccount): Option[DatabaseAccount] = {
    getFromDatastore(una.phone, una.uuid) match {
      case Some(x) => Some(x.head)
      case _ =>
        val user = DatabaseAccount(phone = una.phone, uuid = una.uuid, role = una.role)
        user.saveToDatastore
        Some(user)
    }
  }

  def apply(entity: Entity): DatabaseAccount = new DatabaseAccount(entity)

  def getFromDatastoreRaw(phone: String, uuid: Option[UUID] = None): Option[Seq[Entity]] ={
    val q0 = Query.newEntityQueryBuilder
      .setKind(DatabaseAccount.kind)
      .setFilter(PropertyFilter.eq(propkey_phone, phone))

    val q = uuid match {
      case Some(uuidValue) if uuidValue != "" =>
        q0.setFilter(PropertyFilter.eq(propkey_uuid, uuid.get)).build
      case _ =>
        q0.build
    }

    Option(datastore.run(q, ReadOption.eventualConsistency()).asScala.toSeq)
  }
  override def getFromDatastore(phone: String): Option[Seq[DatabaseAccount]] = getFromDatastore(phone = phone, uuid = None)

  override def getFromDatastore(phone: String, uuid: Option[UUID] = None): Option[Seq[DatabaseAccount]] = {
    getFromDatastoreRaw(phone, uuid) match {
      case Some(results) =>
        Some(results.flatMap(x=>Try(apply(x)).toOption))
      case _ => None
    }
  }
}
case class DatabaseAccount(phone: String, uuid: Option[UUID], role: RoleID = Roles.unknown, email: String = "", address: Address = Address.empty) extends DatastoreStorable {
  import DatabaseAccount._

  val jwt = JwtApi.createTokenWithRole(uuid.getOrElse(""), phone, role)
  var datastoreId: Option[Long] = None

  def this(entity: FullEntity[_]) = {
    this(entity.getString(DatabaseAccount.propkey_phone),
      Option(entity.getString(DatabaseAccount.propkey_uuid)),
      entity.getLong(DatabaseAccount.propkey_role),
      entity.getString(DatabaseAccount.propkey_email),
      new Address(entity.getEntity(DatabaseAccount.propkey_address)))
    datastoreId = Some(entity.getKey().asInstanceOf[Key].getId)
  }
  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    address.asDatastoreEntity match {
      case Some(a) =>
        Option(FullEntity.newBuilder(key)
          .set(propkey_uuid, uuid.getOrElse(""))
          .set(propkey_phone, phone)
          .set(propkey_email, email)
          .set(propkey_role, role)
          .set(propkey_address, a)
          .build)
      case None => None
    }
  }
}