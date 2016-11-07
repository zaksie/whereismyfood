package info.whereismyfood.libs.database

import com.google.cloud.datastore.{Entity, FullEntity}

/**
  * Created by zakgoichman on 10/23/16.
  */
object Databases {
  val persistent = DatastoreClient.instance
  val inmemory = RedisClient.instance
}

trait KVStorable {
  def key: String
}

trait DatastoreStorable {
  protected def datastore = DatastoreClient.instance.client
  def saveToDatastore: Option[Entity] = {
    prepareDatastoreEntity match {
      case Some(record) =>
        Option(datastore.put(record))
      case _ => None
    }
  }
  def prepareDatastoreEntity: Option[FullEntity[_]]
}

trait DatastoreFetchable[E] {
  protected def datastore = DatastoreClient.instance.client
  def getFromDatastore(param: Any): Option[E] = throw new NotImplementedError
  def getFromDatastore(param: String): Option[E] = throw new NotImplementedError
  def getFromDatastore(param1: String, param2: String): Option[E] = throw new NotImplementedError
}
