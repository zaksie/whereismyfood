package info.whereismyfood.modules.order

import java.util.UUID
import javafx.scene.Parent

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{DatastoreStorable, KVStorable}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.menu.{Dish, DishToAdd}
import spray.json.DefaultJsonProtocol

import info.whereismyfood.libs.database.Databases.persistent.{client => datastore}

/**
  * Created by zakgoichman on 11/8/16.
  */
object OrderItem {
  val kind = "OrderItem"
  def of(dish: Dish, x: DishToAdd): Option[OrderItem] = {
    Some(OrderItem(UUID.randomUUID.toString, x.orderId.get, x.businessId.get, dish, x.notes))
  }
  def of(x: DishToAdd): Option[OrderItem] = {
    Dish.find(x.dishId) match {
      case Some(dish) =>
        of(dish, x)
      case _ => None
    }
  }
}
case class OrderItem(id: String, orderId: String, businessId: Long, dish: Dish, notes: String) extends KVStorable {
  import OrderItem._

  def asDatastoreEntity(parentKind: String): Option[FullEntity[_ <: IncompleteKey]] = {
    val key = datastore.newKeyFactory()
        .setKind(kind)
        .addAncestor(PathElement.of(parentKind, orderId))
        .newKey(id)
    val entity = FullEntity.newBuilder(key)
    entity.set("dishId", dish.id) //TODO: add storage and retrieval of full record with `dish`
    entity.set("notes", notes)
    Some(entity.build()) //Some and not option so that hopefully this throws an exception if unable to build
  }

  override def key = businessId + "/" + id
}

object OrderItemJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.modules.menu.DishJsonSupport._
  implicit val itemFormat = jsonFormat5(OrderItem.apply)
}


