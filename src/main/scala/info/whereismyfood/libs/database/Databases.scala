package info.whereismyfood.libs.database

import com.google.cloud.datastore.Entity

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
  val datastore = DatastoreClient.instance.client
  def saveToDatastore: Unit
  def prepareDatastoreEntity: Entity
}

trait DatastoreFetchable[E] {
  val datastore = DatastoreClient.instance.client
  def getFromDatastore(params: Any): Option[E]
}
