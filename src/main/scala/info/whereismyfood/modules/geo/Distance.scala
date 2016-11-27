package info.whereismyfood.modules.geo

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.{LatLng => DSLatLng}
import com.google.maps.model.{LatLng => GoogleLatLng}
import info.whereismyfood.libs.database.KVStorable
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

import scala.util.Try

/**
  * Created by zakgoichman on 11/1/16.
  */
case class DistanceParams(meters: Long, seconds: Long)

object LatLng extends DefaultJsonProtocol with SprayJsonSupport{
  def apply(geoPt: GoogleLatLng) = new LatLng(geoPt)
  def apply(dsPt: DSLatLng) = new LatLng(dsPt)

  def fromGeoId(id: String): Option[LatLng] = Try {
    val coords = id.split(',').map(_.toDouble)
    LatLng(coords(0), coords(1))
  }.toOption

  implicit val credsFormatter = jsonFormat(LatLng.apply, "lat", "lng")
}
case class LatLng(lat: Double, lng: Double){
  def isValid: Boolean = Math.abs(lat) <= 180 && Math.abs(lng) <= 90

  def this(latLng: GoogleLatLng) = this(latLng.lat, latLng.lng)
  def this(latLng: DSLatLng) = this(latLng.getLatitude, latLng.getLongitude)
  def toGoogleLatLng = new GoogleLatLng(lat, lng)
  def toDatastoreLatLng = DSLatLng.of(lat, lng)

  override def toString: String = lat.toString + "," + lng.toString
  val geoid : String = toString
}

object Distance {
  val zero = Distance(null, null, 0, 0)
  val log = LoggerFactory.getLogger(this.getClass)
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

  def asTheCrowFlies(p1: LatLng, p2: LatLng): Double = {
    def deg2rad(deg: Double): Double = deg * Math.PI / 180.0
    def rad2deg(rad: Double): Double = rad * 180 / Math.PI

    val coeff = 1.609344 * 60 * 1.1515
    val theta = p1.lng - p2.lng
    val x = Math.sin(deg2rad(p1.lat)) * Math.sin(deg2rad(p2.lat)) + Math.cos(deg2rad(p1.lat)) * Math.cos(deg2rad(p2.lat)) * Math.cos(deg2rad(theta))
    (rad2deg _ compose Math.acos)(x)
  }
}
case class Distance(from: LatLng, to: LatLng, distance_meter: Long, distance_sec: Long) extends KVStorable{
  import Distance._
  def saveToDB: Unit = {
    log.warn("Attempting to use saveToDB on Distance object [NOT ALLOWED]")
  }

  import Distance._

  def getOwnHash: String ={
    getHash(from, to)
  }

  override def key: String = getOwnHash
}

class DistanceEx(val fromAddr:Address, val toAddr: Address,
                      override val distance_meter: Long, override val distance_sec: Long)
  extends Distance(fromAddr.latLng, toAddr.latLng, distance_meter, distance_sec){
  override def saveToDB: Unit = saveToMySQL

  protected def saveToMySQL: Unit = {
    GeoMySQLInterface.saveAddressAndLocation(fromAddr, toAddr)
    GeoMySQLInterface.saveDistances(from, to, distance_meter, distance_sec)
  }
}

