package info.whereismyfood.libs.geo

import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.{FullEntity, PathElement}
import info.whereismyfood.libs.database.{Databases, DatastoreStorable}
import info.whereismyfood.models.user.{Creds, GenericUser}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import redis.ByteStringFormatter

import scala.util.Try
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 11/7/16.
  */
object BrowserGeolocation{
  implicit val byteStringFormatter = new ByteStringFormatter[BrowserGeolocation] {
    override def serialize(data: BrowserGeolocation): ByteString = {
      val pickled = Pickle.intoBytes[BrowserGeolocation](data)
      ByteString(pickled)
    }
    override def deserialize(bs: ByteString): BrowserGeolocation = {
      Unpickle[BrowserGeolocation].fromBytes(bs.asByteBuffer)
    }
  }

  val kind = "BrowserGeolocation"

  def register(positionStr: String)(implicit user: GenericUser): Option[BrowserGeolocation] = {
    Try {
      val json = JsonMethods.parse(positionStr)
      implicit val formats = DefaultFormats

      json.extract[BrowserGeolocation].register

    }.toOption
  }

}
final case class Coords(speed: Double, longitude: Double, latitude: Double,
                        accuracy: Double, heading: Double, altitude: Double,
                        altitudeAccuracy: Double)

final case class BrowserGeolocation(coords: Coords, timestamp: Double) {
  def datastore = Databases.persistent.client

  def register(implicit user: GenericUser): BrowserGeolocation = {
    saveToDatastore(user)
    Databases.inmemory.save(3 hours, (user.phone, this))
    this
  }

  def saveToDatastore(implicit user: GenericUser):Unit = {
    val key = datastore.newKeyFactory().setKind(BrowserGeolocation.kind).addAncestor(user.asDatastoreAncestor).newKey()
    val entity = FullEntity.newBuilder(key)
    for (field <- coords.getClass.getDeclaredFields) {
      field.setAccessible(true)
      entity.set(field.getName, field.get(coords).asInstanceOf[Double])
    }
    entity.set("timestamp", timestamp)
    Option(datastore.put(entity.build()))
  }
}


