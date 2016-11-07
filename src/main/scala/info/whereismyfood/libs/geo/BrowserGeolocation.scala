package info.whereismyfood.libs.geo

import akka.util.ByteString
import boopickle.Default._
import redis.ByteStringFormatter

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
}
final case class Coords(speed: Double, longitude: Double, latitude: Double,
                        accuracy: Double, heading: Double, altitude: Double,
                        altitudeAccuracy: Double)

final case class BrowserGeolocation(coords: Coords, timestamp: Double)
