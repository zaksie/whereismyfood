package info.whereismyfood.libs.database

import com.google.appengine.api.datastore.{DatastoreService, DatastoreServiceFactory}
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/23/16.
  */
class DatastoreClient private {
  private val log = LoggerFactory.getLogger(this.getClass)
  private val helper = new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig())
  helper.setUp()

  val client : DatastoreService = DatastoreServiceFactory.getDatastoreService()

  def get[T <: DatastoreStorable[T]](t: T, params: String) : Any = t.getFromDatastore(params)

  def save[T <: DatastoreStorable[T]](ts: T*): Unit = {
    ts.foreach(_.saveToDatastore)
  }
}

object DatastoreClient{
  val instance = new DatastoreClient
}

