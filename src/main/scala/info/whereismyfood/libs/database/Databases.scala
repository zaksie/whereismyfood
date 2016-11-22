package info.whereismyfood.libs.database

import com.google.cloud.datastore.{Entity, FullEntity}

/**
  * Created by zakgoichman on 10/23/16.
  */
object Databases {
  val persistent = DatastoreClient.instance
  val sql = CloudSQLClient.connection
  val inmemory = RedisClient.instance

  def init: Unit = None
}

trait KVStorable {
  def key: String
}