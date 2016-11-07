package info.whereismyfood.libs.auth

import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}
import collection.JavaConverters._



object UnverifiedAccount extends DatastoreFetchable[UnverifiedAccount] {
  val kind = "UnverifiedAccount"
  val propkey_uuid = "uuid"
  val propkey_code = "code"
  val propkey_phone = "phone"
  val propkey_role = "role"

  def save(uuid: String, phone: String, code: String): Boolean = {
    val una = UnverifiedAccount(uuid, phone, code)
    /*DatabaseAccount.getFromDatastore(una.uuid, una.phone) match {
      case Some(_) => false
      case None => una.saveToDatastore; true
    }*/
    una.saveToDatastore
    true
  }

  def verify(creds: Creds): Option[DatabaseAccount] = {
    val una = new UnverifiedAccount(creds)
    getFromDatastore(una) match {
      case Some(found) => {
        if(found.matches(una)) {
          DatabaseAccount.add(found)
        }
        else None
      }
      case None => None
    }
  }

  override def getFromDatastore(_account: Any): Option[UnverifiedAccount] = {
    val account = _account.asInstanceOf[UnverifiedAccount]
    val q: Query[Entity] = Query.newEntityQueryBuilder()
      .setKind(kind)
      .setFilter(PropertyFilter.eq(propkey_phone, account.phone))
      .build()
    val result: QueryResults[Entity] = datastore.run(q, ReadOption.eventualConsistency())

    if(!result.hasNext) None
    else {
      val r = result.next
      Some(UnverifiedAccount(
        r.getString(propkey_uuid),
        r.getString(propkey_phone),
        r.getString(propkey_code),
        r.getLong(propkey_role)
      ))
    }
  }
}

final case class UnverifiedAccount(uuid: String, phone: String, code: String, role: Long = Roles.client) extends DatastoreStorable{
  import UnverifiedAccount._

  def this(a: Creds) = this(a.uuid, a.phone, a.code)
  def matches(a: UnverifiedAccount): Boolean =
    code == a.code && phone == a.phone && uuid == a.uuid && role == a.role
  override def prepareDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey(phone)
    Option(Entity.newBuilder(key)
      .set(propkey_uuid, uuid)
      .set(propkey_phone, phone)
      .set(propkey_code, code)
      .set(propkey_role, role)
      .build())
  }
}