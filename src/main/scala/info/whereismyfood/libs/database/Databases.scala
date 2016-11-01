package info.whereismyfood.libs.database


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

trait DatastoreStorable[E] {
  val datastore = DatastoreClient.instance.client
  def saveToDatastore: Unit
  def getFromDatastore(key1: String): Option[E]
}
