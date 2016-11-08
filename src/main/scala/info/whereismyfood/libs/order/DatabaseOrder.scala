package info.whereismyfood.libs.order

import com.google.cloud.datastore.{FullEntity, PathElement}
import info.whereismyfood.libs.auth.DatabaseAccount
import info.whereismyfood.libs.database.{Databases, DatastoreFetchable, DatastoreStorable}

import scala.concurrent.Future
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 11/8/16.
  */
object DatabaseOrder extends DatastoreFetchable[Order]{
  val kind = "Order"
  val propkey_timestamp = "timestamp"
  val propkey_courier = "courier"
  val propkey_items = "items"
  import scala.concurrent.ExecutionContext.Implicits.global

  def save(order: Order): Boolean = {
    import OrderItemCompanion._
    Future(Databases.inmemory.addToList(key = order.recipient.phone + "_order_list", items = order.contents.items, expiry = 30 days))
    Future(DatabaseOrder(order).saveToDatastore)

    //TODO: check or dont check if saved in db
    true
  }
}

case class DatabaseOrder(order: Order) extends DatastoreStorable {
  import DatabaseOrder._

  override def asDatastoreEntity: Option[FullEntity[_]] = {
    DatabaseAccount.getFromDatastore(order.recipient.phone) match {
      case Some(records) => {
        val record = records(0)
        val courier = DatabaseAccount.getFromDatastore(order.courier.phone)
        if(record.datastoreId.isEmpty || courier.isEmpty || courier.get.length < 1) return None

        val ancestor = PathElement.of(DatabaseAccount.kind, record.datastoreId.get)

        val key = datastore.newKeyFactory().setKind(kind).addAncestor(ancestor).newKey()
        val entity = FullEntity.newBuilder(key)
        entity.set(propkey_courier, courier.get.head.datastoreId.get)
        entity.set(propkey_items, order.contents.asDatastoreEntityList)
        Option(entity.build())
      }
      case _ => None
    }
  }
}