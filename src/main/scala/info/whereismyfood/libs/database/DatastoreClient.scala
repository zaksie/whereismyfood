package info.whereismyfood.libs.database


import com.google.cloud.datastore._
import org.slf4j.LoggerFactory

import scala.util.Try

/**
  * Created by zakgoichman on 10/23/16.
  */
class DatastoreClient private {

  val client = DatastoreOptions.getDefaultInstance.getService

  def save[T <: DatastoreStorable](ts: T*): Unit = {
    client.put(ts.flatMap(_.asDatastoreEntity):_*)
  }
}

object DatastoreClient{
  val log = LoggerFactory.getLogger(this.getClass)
  val instance = new DatastoreClient
}

trait DatastoreStorable {
  protected def datastore = DatastoreClient.instance.client
  def saveToDatastore(): Option[Entity] = {
    asDatastoreEntity match {
      case Some(record) =>
        Option(datastore.put(record))
      case _ => None
    }
  }
  def removeFromDatastore(): Boolean = {
    getDatastoreKey match {
      case Some(key) =>
        try{
          datastore.delete(key)
          true
        }catch{
          case e: Throwable =>
            DatastoreClient.log.error("Failed to delete record from datastore", e)
            false
        }
      case _ => false
    }
  }
  def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]]
  def getDatastoreKey: Option[Key] = None
}

trait DatastoreFetchable[E] {
  protected def datastore = DatastoreClient.instance.client
}

