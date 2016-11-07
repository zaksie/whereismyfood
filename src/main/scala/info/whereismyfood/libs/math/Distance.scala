package info.whereismyfood.libs.math

import boopickle.Default._
import akka.util.ByteString
import com.google.cloud.datastore.{Entity, FullEntity, Key, ReadOption, LatLng => DSLatLng}
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable, KVStorable}
import redis.ByteStringFormatter
import com.google.maps.model.{LatLng => GoogleLatLng}

/**
  * Created by zakgoichman on 11/1/16.
  */

object LatLng {
  def apply(geoPt: GoogleLatLng) = new LatLng(geoPt)
}

case class LatLng(lat: Double, lng: Double){
  def this(latLng: GoogleLatLng) = this(latLng.lat, latLng.lng)
  def this(latLng: DSLatLng) = this(latLng.getLatitude, latLng.getLongitude)
  def toGoogleLatLng = new GoogleLatLng(lat, lng)
  def toDatastoreLatLng = DSLatLng.of(lat, lng)

  override def toString: String = lat.toString + "," + lng.toString
}
object Location {
  def apply(name: String, geoPt: DSLatLng) = new Location(name, geoPt)
  def apply(name: String, geoPt: GoogleLatLng) = new Location(name, geoPt)
}
case class Location(name: String, latLng: LatLng){
  def this(name: String, geoPt: DSLatLng) = this(name, LatLng(geoPt.getLatitude, geoPt.getLongitude))
  def this(name: String, geoPt: GoogleLatLng) = this(name, LatLng(geoPt))

  val geoid : String = latLng.toString
  def toGeoPt: DSLatLng = DSLatLng.of(latLng.lat, latLng.lng)
}

object Distance extends DatastoreFetchable[Distance] {
  val zero = Distance(null, null, 0, 0)
  val propkey_distanceInMeters = "distanceInMeters"
  val propkey_timeInSeconds = "timeInSeconds"
  val propkey_from = "phone"
  val propkey_to = "phone"
  val propkey_fromName = "fromName"
  val propkey_toName = "toName"
  val kind = "Distance"

  implicit val byteStringFormatter = new ByteStringFormatter[Distance] {
    override def serialize(data: Distance): ByteString = {
      val pickled = Pickle.intoBytes[Distance](data)
      ByteString(pickled)
    }
    override def deserialize(bs: ByteString): Distance = {
      Unpickle[Distance].fromBytes(bs.asByteBuffer)
    }
  }

  def getHash(from: LatLng, to: LatLng): String = {
    from.toString + ":" + to.toString
  }

  def getHash(from: Location, to: Location): String = {
    getHash(from.latLng, to.latLng)
  }
  def getFromDB(param: Any): Option[Distance] = getFromDatastore(param)

  override def getFromDatastore(hashcode: String): Option[Distance] = {
    val distanceKey: Key = datastore.newKeyFactory().setKind(kind).newKey(hashcode)
    val result = datastore.get(distanceKey, ReadOption.eventualConsistency())
    val from = Location(result.getString(propkey_fromName), result.getLatLng(propkey_from))
    val to = Location(result.getString(propkey_toName), result.getLatLng(propkey_to))
    Some(Distance(from, to,
      result.getLong(propkey_distanceInMeters),
      result.getLong(propkey_timeInSeconds)))
  }
}
case class Distance(from: Location, to: Location, distanceInMeters: Long, timeInSeconds: Long)
  extends KVStorable with DatastoreStorable {
  import Distance._

  def this(e: Entity) = {
    this(Location(e.getString(Distance.propkey_fromName), e.getLatLng(Distance.propkey_from)),
      Location(e.getString(Distance.propkey_toName), e.getLatLng(Distance.propkey_to)),
      e.getLong(Distance.propkey_distanceInMeters),
      e.getLong(Distance.propkey_timeInSeconds))
  }

  def getOwnHash: String ={
    getHash(from, to)
  }

  override def key: String = getOwnHash

  override def prepareDatastoreEntity: Option[FullEntity[_]] = {
    val distanceKey = datastore.newKeyFactory().setKind(kind).newKey(key)
    Option(Entity.newBuilder(distanceKey)
      .set(propkey_distanceInMeters, distanceInMeters)
      .set(propkey_timeInSeconds, timeInSeconds)
      .set(propkey_from, from.toGeoPt)
      .set(propkey_to, to.toGeoPt)
      .set(propkey_fromName, from.name)
      .set(propkey_toName, to.name)
      .build())
  }
}

