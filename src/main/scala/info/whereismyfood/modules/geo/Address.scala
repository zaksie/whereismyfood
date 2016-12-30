package info.whereismyfood.modules.geo

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.ask
import com.google.cloud.datastore.FullEntity
import com.google.maps.model.GeocodingResult
import info.whereismyfood.libs.database.DatastoreStorable
import info.whereismyfood.libs.geo.AddressToLatLng
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
/**
  * Created by zakgoichman on 11/4/16.
  */
object Address extends DefaultJsonProtocol with SprayJsonSupport{
  private val log = LoggerFactory.getLogger(this.getClass)

  val kind = "Address"
  val _latlng = "latlng"
  val _raw = "raw"

  def empty = Address(LatLng(0,0))
  implicit val addressFormatter = jsonFormat(Address.apply, "latLng", "raw")

  def of(addressOption: Option[String]): Option[Address] = {
    addressOption match {
      case Some(address) =>
        import info.whereismyfood.aux.ActorSystemContainer.Implicits._
        val f = GeoMySQLInterface.findByAddress(address).flatMap {
          case Some(latLng) =>
            Future.successful(Some(Address(latLng, address)))
          case _ =>
            system.actorSelection("/user/libs/google-geocoding-api").resolveOne().flatMap { aref =>
              aref ? AddressToLatLng(address) map {
                case x: GeocodingResult =>
                  log.info("in Address.of with result: {}", x)
                  Some(Address(LatLng(x.geometry.location), address))
                case x =>
                  println(x)
                  None
              }
            }
        }

        Await.result(f, 30 seconds) match {
          case Some(addr) if !addr.latLng.isValid =>
            log.error(s"LatLng(${addr.latLng}) is invalid!")
            None
          case addr@Some(_) => addr
          case _ => None
        }
      case _ => None
    }
  }
}
case class Address(latLng: LatLng, raw: String = "") extends DatastoreStorable{
  def geoid = latLng.geoid

  import Address._
  def this(entity: FullEntity[_]) = {
    this(new LatLng(entity.getLatLng(Address._latlng)),
      entity.getString(Address._raw))
  }

  override def saveToDatastore = throw new UnsupportedOperationException

  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
    entity.set(_latlng, latLng.toDatastoreLatLng)
    try {
      for (field <- this.getClass.getDeclaredFields) {
        field.setAccessible(true)
        field.get(this) match {
          case s: String =>
            entity.set(field.getName, s)
          case _ =>
        }
      }
    }catch{
      case e: Exception => log.error("Error in Address save to datastore: {}", e)
    }
    Option(entity.build())
  }
}

