package info.whereismyfood.libs.auth

import com.google.cloud.datastore.{Entity, Key, ReadOption}
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}



object UnverifiedAccountCompanion extends DatastoreFetchable[UnverifiedAccount] {
  val kind = "UnverifiedAccount"
  val propkey_uuid = "uuid"
  val propkey_code = "code"
  val propkey_phonenumber = "phonenumber"
  def save(uuid: String, to: String, code: String)={
    val ua = UnverifiedAccount(uuid, to, code)
    ua.saveToDatastore
  }
  def get(uuid: String): Option[UnverifiedAccount] = {
    getFromDatastore(uuid)
  }

  override def getFromDatastore(param: Any): Option[UnverifiedAccount] = {
    val uuid: String = param.asInstanceOf[String]
    val key: Key = datastore.newKeyFactory().setKind(kind).newKey(uuid)
    val result = datastore.get(key, ReadOption.eventualConsistency())
    Some(UnverifiedAccount(
      result.getString(propkey_uuid),
      result.getString(propkey_phonenumber),
      result.getString(propkey_code)
    ))
  }
}

final case class UnverifiedAccount(uuid: String, to: String, code: String) extends DatastoreStorable{
  import UnverifiedAccountCompanion._

  def this(a: Account) = this(a.uuid, a.to, a.code)

  override def saveToDatastore: Unit ={
    datastore.put(prepareDatastoreEntity)
  }

  override def prepareDatastoreEntity: Entity = {
    val key = datastore.newKeyFactory().setKind(kind).newKey(uuid)
    Entity.newBuilder(key)
      .set(propkey_uuid, uuid)
      .set(propkey_phonenumber, to)
      .set(propkey_code, code)
      .build()
  }

  def isValid: Boolean = {
    val result = get(uuid)
    result.isDefined && this == result.get
  }
}