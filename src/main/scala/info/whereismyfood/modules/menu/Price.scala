package info.whereismyfood.modules.menu

import java.text.DecimalFormat

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.{FullEntity, IncompleteKey}
import info.whereismyfood.libs.database.DatastoreStorable
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 1/14/17.
  */
object Price{
  def getReadable(value: Double, currency: String) = {
    val formatter = new DecimalFormat("#.##")
    formatter.format(value) + currency
  }

  object FieldNames {
    val _humanReadable = "humanReadable"
    val _value = "value"
    val _currency = "currency"
  }

  //Throws exception if creation of Price object fails
  def of(entity: FullEntity[_]): Price = {
    import FieldNames._
    val value = entity.getDouble(_value)
    val currency = entity.getString(_currency)
    Price(value, currency)
  }

  val kind = "Price"
}
case class Price(value: Double, currency: String, var humanReadable: Option[String] = None) extends DatastoreStorable{
  import Price._

  if(humanReadable.isEmpty) humanReadable = Some(getReadable(value, currency))

  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
        .set(FieldNames._value, value)
        .set(FieldNames._currency, currency)
        .build
    Some(entity) //Some and not option so that hopefully this throws an exception if unable to build
  }
}

object PriceJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val priceFormat = jsonFormat3(Price.apply)
}