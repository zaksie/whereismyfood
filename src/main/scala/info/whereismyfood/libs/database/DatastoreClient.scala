package info.whereismyfood.libs.database


import com.google.cloud.datastore.DatastoreOptions
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/23/16.
  */
class DatastoreClient private {
  private val log = LoggerFactory.getLogger(this.getClass)

  val client = DatastoreOptions.getDefaultInstance.getService

  def save[T <: DatastoreStorable](ts: T*): Unit = {
    client.put(ts.flatMap(_.prepareDatastoreEntity):_*)
  }
}

object DatastoreClient{
  val instance = new DatastoreClient
}

