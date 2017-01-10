package info.whereismyfood.modules.menu

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{Databases, DatastoreStorable}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.order.OrderItem
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol

import scala.util.Try

/**
  * Created by zakgoichman on 12/30/16.
  */
object Price{
  object FieldNames {
    val _humanReadable = "humanReadable"
    val _value = "value"
    val _currency = "currency"
  }

  //Throws exception if creation of Price object fails
  def of(entity: FullEntity[_]): Price = {
    import FieldNames._
    Price(entity.getString(_humanReadable),
      entity.getDouble(_value),
      entity.getString(_currency))
  }

  val kind = "Price"
}
case class Price(humanReadable: String, value: Double, currency: String) extends DatastoreStorable{
  import Price._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
    for (field <- this.getClass.getDeclaredFields) {
      field.setAccessible(true)
      if(field.getType.isAssignableFrom(classOf[String]))
        entity.set(field.getName, field.get(this).asInstanceOf[String])
      else if(field.getType.isAssignableFrom(classOf[Double]))
        entity.set(field.getName, field.get(this).asInstanceOf[Double])
    }
    Some(entity.build()) //Some and not option so that hopefully this throws an exception if unable to build
  }
}

object Dish {
  object FieldNames {
    val title = "title"
    val description = "description"
    val image = "image"
    val price = "price"
  }

  protected def datastore: Datastore = Databases.persistent.client
  protected val log: Logger = LoggerFactory.getLogger(getClass.getName)

  def getFromDatastore(businessId: Long, dishId: Long): Option[Dish] = {
    val parent = PathElement.of(Business.kind, businessId)
    val key = datastore.newKeyFactory().addAncestor(parent).setKind(kind).newKey(dishId)
    Try {
      of {
        datastore.get(key, ReadOption.eventualConsistency)
      }.get
    }.toOption
  }

  def of(entity: Entity): Option[Dish] = {
    Try {
      import FieldNames._
        Dish(entity.getKey.getId,
          entity.getKey.getParent.getId,
          entity.getString(title),
          entity.getString(image),
          entity.getString(description),
          Price.of(entity.getEntity(price)))
    }.toOption
  }

  def find(businessId: Long, dishId: Long): Option[Dish] = {
    getFromDatastore(businessId, dishId)
  }

  def addOrChange(dish: Dish): Boolean = {
    import FieldNames._
    log.info("Saving dish to datastore")
    try {
      val ancestor = PathElement.of(Business.kind, dish.businessId)
      val keyBase = datastore.newKeyFactory().setKind(kind).addAncestor(ancestor)
      val key = if (dish.id > 0) keyBase.newKey(dish.id) else keyBase.newKey()
      val entity = FullEntity.newBuilder(key)
          .set(title, dish.title)
          .set(image, dish.image)
          .set(description, dish.description)
          .set(price, dish.price.asDatastoreEntity.get)
          .build
      datastore.put(entity)
      true
    }catch{
      case e: Throwable =>
        log.error("Failed to add or change dish", e)
        false
    }
  }

  def remove(dish: Dish): Boolean = {
    log.info("Removing dish from datastore")
    try {
      val ancestor = PathElement.of(Business.kind, dish.businessId)
      val key = datastore.newKeyFactory().setKind(kind).addAncestor(ancestor).newKey(dish.id)
      datastore.delete(key)
      true
    }catch{
      case e: Throwable =>
        log.error("Failed to remove dish", e)
        false
    }
  }

  val kind = "Dish"
}
case class Dish(id: Long, businessId: Long, title: String, image: String, description: String, price: Price) extends DatastoreStorable{
  import Dish._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    val parent = PathElement.of(Business.kind, businessId)
    val key = datastore.newKeyFactory().addAncestor(parent).setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
    for (field <- this.getClass.getDeclaredFields) {
      field.setAccessible(true)
      if(field.getType.isAssignableFrom(classOf[String]))
        entity.set(field.getName, field.get(this).asInstanceOf[String])
      else if(field.getType.isAssignableFrom(classOf[Long]))
        entity.set(field.getName, field.get(this).asInstanceOf[Long])
    }

    price.asDatastoreEntity match {
      case Some(priceEntity) =>
        entity.set("price", priceEntity)
      case _ =>
    }

    Some(entity.build()) //Some and not option so that hopefully this throws an exception if unable to build
  }
}
case class DishToAdd(businessId: Long, dishId: Long, notes: String){
  def toOrderItem: Option[OrderItem] = OrderItem.of(this)
}
case class DishToRemove(orderItemId: Long)

object DishJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val dishToAddFormat = jsonFormat3(DishToAdd)
  implicit val dishToRemoveFormat = jsonFormat1(DishToRemove)
  implicit val priceFormat = jsonFormat3(Price.apply)
  implicit val dishFormat = jsonFormat6(Dish.apply)
}