package info.whereismyfood.libs.math

import scala.pickling.Defaults._
import scala.pickling.json._
import akka.util.ByteString
import com.google.appengine.api.datastore.{Entity, GeoPt, KeyFactory}
import com.google.maps.model.LatLng
import info.whereismyfood.libs.database.{DatastoreStorable, KVStorable}
import redis.ByteStringFormatter

/**
  * Created by zakgoichman on 10/25/16.
  */

object Location {
  def apply(name: String, geoPt: GeoPt) = new Location(name, geoPt)
}
case class Location(name: String, latLng: LatLng){
  def this(name: String, geoPt: GeoPt) = this(name, new LatLng(geoPt.getLatitude, geoPt.getLongitude))

  val geoid : String = latLng.toString
  def toGeoPt: GeoPt = new GeoPt(latLng.lat.toFloat, latLng.lng.toFloat)
}

object Distance {
  val zero = Distance(null, null, 0, 0)
  val propkey_distanceInMeters = "distanceInMeters"
  val propkey_timeInSeconds = "timeInSeconds"
  val propkey_from = "from"
  val propkey_to = "to"
  val propkey_fromName = "fromName"
  val propkey_toName = "toName"
  val kind = "Distance"

  implicit val byteStringFormatter = new ByteStringFormatter[Distance] {
    override def serialize(data: Distance): ByteString = {
      val pickled = this.pickle
      ByteString(pickled.value)
    }
    override def deserialize(bs: ByteString): Distance = {
      bs.utf8String.unpickle[Distance]
    }
  }
}
case class Distance(from: Location, to: Location, distanceInMeters: Long, timeInSeconds: Long) extends KVStorable with DatastoreStorable[Distance] {
  type DTYPE = Distance
  import Distance._
  override def hashCode(): Int ={
    from.geoid.hashCode * 104729 + to.geoid.hashCode
  }

  override def key: String = hashCode.toString

  override def saveToDatastore: Unit = {
    val distanceEntity = new Entity("Distance", hashCode.toString)
    distanceEntity.setProperty(propkey_distanceInMeters, distanceInMeters)
    distanceEntity.setProperty(propkey_timeInSeconds, timeInSeconds)
    distanceEntity.setProperty(propkey_from, from.toGeoPt)
    distanceEntity.setProperty(propkey_to, to.toGeoPt)
    distanceEntity.setProperty(propkey_from, from.name)
    distanceEntity.setProperty(propkey_to, to.name)

    datastore.put(distanceEntity)
  }

  override def getFromDatastore(hashcode: String): Option[Distance] = {
    val key = KeyFactory.createKey(kind, hashcode);
    val result = datastore.get(key)
    val from = Location(result.getProperty(propkey_fromName).asInstanceOf[String], result.getProperty(propkey_from).asInstanceOf[GeoPt])
    val to = Location(result.getProperty(propkey_toName).asInstanceOf[String], result.getProperty(propkey_to).asInstanceOf[GeoPt])
    Some(Distance(from, to,
      result.getProperty(propkey_distanceInMeters).asInstanceOf[Long],
      result.getProperty(propkey_timeInSeconds).asInstanceOf[Long]))
  }
}

class DistanceMatrix  {
  private val distances = new scala.collection.mutable.ArrayBuffer[Distance]

  def this(ds: Seq[Distance]) = {
    this
    add(ds:_*)
  }

  def add(ds: Distance*): DistanceMatrix = {
    for(d <- ds) {
      val hash = d.hashCode
      if (!distances.exists(_.hashCode == hash)) {
        distances.append(d)
      }
    }
    this
  }

  def getAll : Seq[Distance] = distances
  def get(from: LatLng, to: LatLng): Option[Distance] = distances.find(d => d.from.latLng == from && d.to.latLng == to)
  def size = {
    distances.map(_.from).distinct.length
  }

  def merge(dm2: DistanceMatrix): DistanceMatrix = {
    this.add(dm2.distances: _*)
    this
  }
}

