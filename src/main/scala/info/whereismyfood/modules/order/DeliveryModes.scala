package info.whereismyfood.modules.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.{FullEntity, IncompleteKey}
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}
import info.whereismyfood.modules.order.DeliveryModes.Mode
import spray.json.DefaultJsonProtocol

import scala.util.Try

/**
  * Created by zakgoichman on 1/15/17.
  */
object DeliveryModes extends DatastoreFetchable[DeliveryMode]{

  type Mode = String
  val none: Mode = "none"
  val sitin: Mode = "sitin"
  val takeaway: Mode = "takeaway"
  val delivery: Mode = "delivery"

  val _mode = "mode"
  val _value = "value"

  val NA = DeliveryMode(none, "")

  def of(entity: FullEntity[_ <: IncompleteKey]): Option[DeliveryMode] = {
    Try {
      DeliveryMode(
        entity.getString(_mode),
        entity.getString(_value)
      )
    }.toOption
  }
}

case class DeliveryMode(mode: String, value: String) extends DatastoreStorable{

  import DeliveryModes._
  def isDelivery: Boolean = mode == delivery
  def isSitIn: Boolean = mode == sitin
  def isTakeAway: Boolean = mode == takeaway

  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val entity = FullEntity.newBuilder()
      entity.set(_mode, mode)
      entity.set(_value, value)
      entity.build()
    }.toOption
  }
}

object DeliveryModeJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val deliveryModeFormat = jsonFormat2(DeliveryMode)
}
