package info.whereismyfood.modules.menu

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.DatastoreStorable
import info.whereismyfood.modules.order.OrderItem
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.util.Try
/**
  * Created by zakgoichman on 12/30/16.
  */

object Dish extends Common[Dish]{
  object FieldNames {
    val title = "title"
    val description = "description"
    val image = "image"
    val price = "price"
    val businesses = "businesses"
  }

  val kind = "Dish"


  def apply(entity: Entity): Dish = {
    of(entity).get
  }

  def of(entity: Entity): Option[Dish] = {
    Try {
      import FieldNames._
      Dish(entity.getKey.getId,
        entity.getList[LongValue](businesses).asScala.map(_.get.toLong),
        entity.getString(title),
        entity.getString(image),
        entity.getString(description),
        Price.of(entity.getEntity(price)))
    }.toOption
  }
}

case class Dish(id: Long, businessIds: Seq[Long], title: String,
                image: String, description: String, price: Price) extends DatastoreStorable{
  import Dish._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val __image = if(id > 0 && image == "") getFromDatastore(id).get.image else image
      val key = datastore.newKeyFactory().setKind(kind)
      FullEntity.newBuilder(if(id > 0) key.newKey(id) else key.newKey())
          .set(FieldNames.title, title)
          .set(FieldNames.description, description)
          .set(FieldNames.image, __image)
          .set(FieldNames.price, price.asDatastoreEntity.get)
          .set(FieldNames.businesses, businessIds.map(LongValue.of).asJava)
          .build
    }.toOption
  }
}
case class DishToAdd(businessId: Long, dishId: Long, notes: String){
  def toOrderItem: Option[OrderItem] = OrderItem.of(this)
}

object DishJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import PriceJsonSupport._
  implicit val dishToAddFormat = jsonFormat3(DishToAdd)
  implicit val dishFormat = jsonFormat6(Dish.apply)
}