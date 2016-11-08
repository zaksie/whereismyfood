package info.whereismyfood.libs.geo

import com.google.cloud.datastore.{FullEntity, PathElement}
import info.whereismyfood.libs.auth.{Creds, DatabaseAccount}
import info.whereismyfood.libs.database.{Databases, DatastoreFetchable, DatastoreStorable, KVStorable}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods

import scala.util.Try

/**
  * Created by zakgoichman on 11/4/16.
  */

object DatabaseGeolocation extends DatastoreFetchable[DatabaseGeolocation] {
  val kind = "Geolocation"
  val parent = "Creds"
}

case class DatabaseGeolocation(browserGeolocation: BrowserGeolocation)(implicit creds: Creds) extends DatastoreStorable with KVStorable {

  import DatabaseGeolocation._

  override def asDatastoreEntity: Option[FullEntity[_]] = {
    DatabaseAccount.getFromDatastore(creds.phone) match {
      case Some(records) =>
        val record = records.head
        if (record.datastoreId.isEmpty) return None

        val ancestor = PathElement.of(DatabaseAccount.kind, record.datastoreId.get)
        val key = datastore.newKeyFactory().setKind(kind).addAncestor(ancestor).newKey()
        val entity = FullEntity.newBuilder(key)
        for (field <- browserGeolocation.coords.getClass.getDeclaredFields) {
          field.setAccessible(true)
          entity.set(field.getName, field.get(browserGeolocation.coords).asInstanceOf[Double])
        }
        entity.set("timestamp", browserGeolocation.timestamp)
        Option(entity.build())
      case _ => None
    }
  }

  override def key: String = creds.phone
}


object GeolocationRegistry {
  def register(position: BrowserGeolocation)(implicit creds: Creds): Unit = {
    DatabaseGeolocation(position).saveToDatastore
    Databases.inmemory.save((creds.phone, position))
  }

  def register(positionStr: String)(implicit creds: Creds): Option[BrowserGeolocation] = {
    Try {
      val json = JsonMethods.parse(positionStr)
      implicit val formats = DefaultFormats

      val position = json.extract[BrowserGeolocation]
      register(position)
      return Some(position)
    }

    None
  }
}
