package info.whereismyfood.libs.database


import com.google.cloud.datastore.{DatastoreOptions, Entity, FullEntity}
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/23/16.
  */
class DatastoreClient private {
  private val log = LoggerFactory.getLogger(this.getClass)

  val client = DatastoreOptions.getDefaultInstance.getService

  def save[T <: DatastoreStorable](ts: T*): Unit = {
    client.put(ts.flatMap(_.asDatastoreEntity):_*)
  }
}

object DatastoreClient{
  val instance = new DatastoreClient
}

trait DatastoreStorable {
  protected def datastore = DatastoreClient.instance.client
  def saveToDatastore: Option[Entity] = {
    asDatastoreEntity match {
      case Some(record) =>
        Option(datastore.put(record))
      case _ => None
    }
  }
  def asDatastoreEntity: Option[FullEntity[_]]
}

trait DatastoreFetchable[E] {
  protected def datastore = DatastoreClient.instance.client
}

