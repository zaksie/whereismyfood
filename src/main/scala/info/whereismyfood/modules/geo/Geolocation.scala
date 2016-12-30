package info.whereismyfood.modules.geo

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.FullEntity
import info.whereismyfood.libs.database.{Databases, KVStorable}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.geo.Geolocation.makeKeyOutOf
import info.whereismyfood.modules.user.{GenericUser, UserRouter}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
/**
  * Created by zakgoichman on 11/7/16.
  */
object Geolocation{
  private val log = LoggerFactory.getLogger(this.getClass)

  implicit val byteStringFormatter = new ByteStringFormatter[Geolocation] {
    override def serialize(data: Geolocation): ByteString = {
      val pickled = Pickle.intoBytes[Geolocation](data)
      ByteString(pickled)
    }
    override def deserialize(bs: ByteString): Geolocation = {
      Unpickle[Geolocation].fromBytes(bs.asByteBuffer)
    }
  }

  val kind = "Geolocation"
  def getFullKeyFor(key: String): String = kind + "-" + key

  def register(positionStr: String)(implicit user: GenericUser): Option[Geolocation] = {
    Try {
      val json = JsonMethods.parse(positionStr)
      implicit val formats = DefaultFormats

      json.extract[GeolocationRaw].process
    }.toOption
  }

  def retrieve(job: Business.JobInBusiness, ids: String*): Seq[Geolocation] = {
    val f = makeKeyOutOf(job, _: String)
    val g = getFullKeyFor _
    Await.result(Databases.inmemory.retrieve[Geolocation]
        (ids.map(g compose f):_*), 10 seconds)
  }

  def makeKeyOutOf(job: Business.JobInBusiness, id: String): String = job + "-" + id
}

final case class Coords(speed: Double, longitude: Double, latitude: Double,
                        accuracy: Double, heading: Double, altitude: Double,
                        altitudeAccuracy: Double) {
  def toLatLng: LatLng = LatLng(latitude, longitude)
}

final case class GeolocationRaw(coords: Coords, timestamp: Double){
  def process(implicit user: GenericUser): Geolocation = Geolocation(user.jobInBusiness, user.phone, coords, timestamp)
}

final case class Geolocation(job: Business.JobInBusiness, userId: String, coords: Coords, timestamp: Double) extends KVStorable {

  private def datastore = Databases.persistent.client
  private val __key: String = makeKeyOutOf(job, userId)
  register()
  private def register() = {
    import Geolocation._
    saveToDatastore()
    Databases.inmemory.save[Geolocation](3 hours, (getFullKeyFor(key), this))
  }

  private def saveToDatastore(): Unit = {
    import Geolocation._
    Try {
      UserRouter.getByJob(job).get.find(userId).get
    }.toOption match {
      case Some(user) =>
        log.info("Saving geolocation to datastore")
        val key = datastore.newKeyFactory().setKind(kind).addAncestor(user.asDatastoreAncestor).newKey()
        val entity = FullEntity.newBuilder(key)
        for (field <- coords.getClass.getDeclaredFields) {
          field.setAccessible(true)
          entity.set(field.getName, field.get(coords).asInstanceOf[Double])
        }
        entity.set("timestamp", timestamp)
        Option(datastore.put(entity.build()))
      case _ => log.error("Failed to save geolocation because user was not found")
    }
  }

  override def key: String = __key
}

object CoordsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val coordsFormatter = jsonFormat7(Coords)
}
object GeolocationJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import CoordsJsonSupport._
  implicit val formatter = jsonFormat4(Geolocation.apply)
}

