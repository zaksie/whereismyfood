package info.whereismyfood.libs.geo

import com.google.cloud.datastore.{Entity, FullEntity}
import info.whereismyfood.libs.database.DatastoreStorable
import info.whereismyfood.libs.math.LatLng
import scala.reflect.runtime.universe._
/**
  * Created by zakgoichman on 11/4/16.
  */
object Address {
  val kind = "Address"
  val propkey_latlng = "latlng"
  val propkey_street = "street"
  val propkey_buildingNo = "buildingNo"
  val propkey_suiteNo = "suiteNo"
  val propkey_city = "city"
  val propkey_country = "country"
  val propkey_zipCode = "zipCode"

  def empty = Address(LatLng(0,0))
}
case class Address(latLng: LatLng, street: String = "", buildingNo: String = "", suiteNo: String = "",
                   city: String = "", country: String = "", zipCode: String = "") extends DatastoreStorable{
  import Address._
  def this(entity: FullEntity[_]) =
    this(new LatLng(entity.getLatLng(Address.propkey_latlng)),
      entity.getString(Address.propkey_street),
      entity.getString(Address.propkey_buildingNo),
      entity.getString(Address.propkey_suiteNo),
      entity.getString(Address.propkey_city),
      entity.getString(Address.propkey_country),
      entity.getString(Address.propkey_zipCode))

  override def saveToDatastore = throw new UnsupportedOperationException

  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
    entity.set(propkey_latlng, latLng.toDatastoreLatLng)
    for (field <- this.getClass.getDeclaredFields) {
      field.setAccessible(true)
      val value = field.get(this)
      if(value.isInstanceOf[String]) {
        entity.set(field.getName, value.asInstanceOf[String])
      }
    }
    Option(entity.build())
  }
}