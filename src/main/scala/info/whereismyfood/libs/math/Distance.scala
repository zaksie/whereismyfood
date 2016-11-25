package info.whereismyfood.libs.math

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.maps.model.{LatLng => GoogleLatLng}
import com.google.cloud.datastore.{LatLng => DSLatLng}
import info.whereismyfood.libs.database.KVStorable
import info.whereismyfood.libs.geo.{Address, GeoMySQLInterface}
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/1/16.
  */
//TODO: rewrite with cloudsql instead of datastore


case class DistanceParams(meters: Long, seconds: Long)

object LatLng extends DefaultJsonProtocol with SprayJsonSupport{
  def apply(geoPt: GoogleLatLng) = new LatLng(geoPt)
  def apply(dsPt: DSLatLng) = new LatLng(dsPt)
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

