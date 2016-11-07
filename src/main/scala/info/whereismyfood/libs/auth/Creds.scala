package info.whereismyfood.libs.auth

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Value, _}
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
  val client: Long = 0x01
  val courier: Long = 0x02
  val chef: Long = 0x04
  val manager: Long = 0x08

  def apply(str: String): Option[Long] = {
    val value = str.toLong
    if (value > manager || value < client)
      None
    else
      Some(value)
  }

  def apply(str: Seq[String]): Option[Long] = {
    val result: Seq[Long] = str.flatMap(x=>apply(x))
    if(result.length != str.length) None
    else Some(result.reduce(_ & _))
  }
}

final case class Creds(uuid: String="web", phone: String, dbid: Long = -1, code: String = "", role: Long = Roles.client)

object CredsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val verifiedAccountFormat = jsonFormat5(Creds)
}

object DatabaseAccount extends DatastoreFetchable[Seq[DatabaseAccount]]{
  val log = LoggerFactory.getLogger(DatabaseAccount.getClass)

  val kind = "Creds"
  val propkey_phone = "phone"
  val propkey_uuid = "uuid"
  val propkey_email = "email"
  val propkey_role = "role"
  val propkey_address = "address"

  def find(creds: Creds): Option[DatabaseAccount] = {
    if(creds.phone == "" || creds.uuid == "") None
    else getFromDatastore(creds.uuid, creds.phone) match {
      case Some(accounts) =>
        accounts.filter(_.uuid == creds.uuid) match {
          case Seq() => None
          case x if x.length > 1 =>
            log.error("Found more than one match for account (%s, %s)".format(creds.uuid, creds.phone))
            None
          case single => Some(single.head)
        }
      case _ => None
    }
  }

  def add(una: UnverifiedAccount): Option[DatabaseAccount] = {
    getFromDatastore(una.uuid, una.phone) match {
      case Some(x) => Some(x.head)
      case _ =>
        val user = DatabaseAccount(una.uuid, una.phone, una.role)
        user.saveToDatastore
        Some(user)
    }
  }

  def apply(entity: FullEntity[_]): DatabaseAccount = new DatabaseAccount(entity)

  override def getFromDatastore(uuid: String, phone: String): Option[Seq[DatabaseAccount]] = {
    val q0 = Query.newEntityQueryBuilder
      .setKind(DatabaseAccount.kind)
      .setFilter(PropertyFilter.eq(propkey_phone, phone))

    val q1 = if (uuid == "") q0 else q0.setFilter(PropertyFilter.eq(propkey_uuid, uuid))
    val q = q1.build

    val results = datastore.run(q, ReadOption.eventualConsistency()).asScala
    if (results.isEmpty) None
    else Try(results.map(DatabaseAccount.apply).toSeq) match {
      case Success(v) => Option(v)
      case Failure(e) => {
        log.error("Failed to parse datastore results in DatabaseAccount", e)
        None
      }
    }
  }

  override def getFromDatastore(phone: String): Option[Seq[DatabaseAccount]] = getFromDatastore("", phone)
}

case class DatabaseAccount(uuid: String, phone: String, role: Long, email: String = "", address: Address = Address.empty) extends DatastoreStorable {
  import DatabaseAccount._

  val jwt = JwtApi.createTokenWithRole(uuid, phone, role.toString)
  var datastoreId: Option[Long] = None

  def this(entity: FullEntity[_]) = {
    this(entity.getString(DatabaseAccount.propkey_uuid),
      entity.getString(DatabaseAccount.propkey_phone),
      entity.getLong(DatabaseAccount.propkey_role),
      entity.getString(DatabaseAccount.propkey_email),
      new Address(entity.getEntity(DatabaseAccount.propkey_address)))
    datastoreId = Some(entity.getKey().asInstanceOf[Key].getId)
  }
  override def prepareDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    address.prepareDatastoreEntity match {
      case Some(a) =>
        Option(FullEntity.newBuilder(key)
          .set(propkey_uuid, uuid)
          .set(propkey_phone, phone)
          .set(propkey_email, email)
          .set(propkey_role, role)
          .set(propkey_address, a)
          .build)
      case None => None
    }
  }
}