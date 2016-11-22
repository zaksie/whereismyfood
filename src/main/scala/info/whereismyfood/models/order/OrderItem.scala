package info.whereismyfood.models.order


import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{DatastoreStorable, KVStorable}

/**
  * Created by zakgoichman on 11/8/16.
  */
object OrderItemCompanion {
  val kind = "OrderItem"
}
case class Price(humanReadable: String, value: Double, currency: String)
case class OrderItem(name: String, image: String, description: String,
                     notes: String, price: Price) extends DatastoreStorable with KVStorable{

  import OrderItemCompanion._
  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
    for (field <- this.getClass.getDeclaredFields) {
      field.setAccessible(true)
      if(field.getType.isAssignableFrom(classOf[String]))
        entity.set(field.getName, field.get(this).asInstanceOf[String])
    }
    for (field <- price.getClass.getDeclaredFields) {
      field.setAccessible(true)
      if(field.getType.isAssignableFrom(classOf[String]))
        entity.set("price_" + field.getName, field.get(price).asInstanceOf[String])
      if(field.getType.isAssignableFrom(classOf[Double]))
        entity.set("price_" + field.getName, field.get(price).asInstanceOf[Double])
    }
    Some(entity.build()) //Some and not option so that hopefully this throws an exception if unable to build
  }

  override def key = name
}