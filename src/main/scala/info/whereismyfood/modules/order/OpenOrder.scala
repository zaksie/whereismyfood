package info.whereismyfood.modules.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.{Entity, EntityValue, FullEntity}
import info.whereismyfood.libs.database.{Databases, DatastoreStorable, KVStorable}
import info.whereismyfood.libs.math.Misc
import info.whereismyfood.modules.user.{ClientUser, Creds}
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

import collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try
/**
  * Created by zakgoichman on 11/8/16.
  */

object OpenOrder{
  private val log = LoggerFactory.getLogger(this.getClass)

  val kind = "OpenOrder"
  val _timestamp = "timestamp"
  val _businessId = "businessId"
  val _orderId = "orderId"
  val _items = "items"
  val _clientPhone = "clientPhone"

  implicit val byteStringFormatter = new ByteStringFormatter[OpenOrder] {
    override def serialize(data: OpenOrder): ByteString = {
      val pickled = Pickle.intoBytes[OpenOrder](data)
      ByteString(pickled)
    }
    override def deserialize(bs: ByteString): OpenOrder = {
      Unpickle[OpenOrder].fromBytes(bs.asByteBuffer)
    }
  }

  private def getKey(businessId: Long, phone: String): String = OpenOrder.kind + "/" + businessId + "/" + phone

  def save(order: OpenOrder): Boolean = {
    Try {
      Databases.inmemory.save[OpenOrder](30 day, (order.key, order))
      order.saveToDatastore match {
          case None => throw new Exception("couldn't save open order")
          case _ => None
        }
      }.isSuccess
  }

  def retrieve(businessId: Long, phone: String): Option[OpenOrder] = {
    val name = getKey(businessId, phone)
    Await.result[Seq[OpenOrder]](Databases.inmemory.retrieve[OpenOrder](name), 30 seconds).headOption
  }

  def addItem(businessId: Long, phone: String, item: OrderItem): Boolean = {
    retrieve(businessId, phone) match {
      case Some(order) =>
        val new_order = order.copy(contents = order.contents :+ item)
        save(new_order)
      case _ =>
        ClientUser.find(phone) match {
          case Some(user) =>
            val order = OpenOrder(businessId,
              Misc.generateNumericCode(8),
              System.currentTimeMillis / 1000,
              user.toCreds(),
              Seq(item))
            save(order)
          case _ =>
            false
        }
    }
  }

  def removeItem(businessId: Long, phone: String, itemId: String): Boolean = {
    retrieve(businessId, phone) match {
      case Some(order) =>
        val new_order = order.copy(contents = order.contents.filterNot(_.id == itemId))
        save(new_order)
      case _ =>
        false
    }
  }
}

case class OpenOrder(businessId: Long, id: String, timestamp: Long, client: Creds, contents: Seq[OrderItem])
    extends DatastoreStorable with KVStorable{
  import OpenOrder._
  override def asDatastoreEntity: Option[FullEntity[_]] = {
    
    if(client.uuid.isEmpty) return None
    
    val key = datastore.newKeyFactory().setKind(kind).newKey(client.uuid.get)
    val entity = Entity.newBuilder(key)
    entity.set(_orderId, id)
    entity.set(_timestamp, timestamp)
    entity.set(_businessId, businessId)
    entity.set(_clientPhone, client.phone) // same as client.uuid
    entity.set(_items, contents.map(x => new EntityValue(x.asDatastoreEntity.get)).asJava)

    Option(entity.build())
  }

  override def key: String = OpenOrder.getKey(businessId, client.uuid.get)
}

object OpenOrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import OrderJsonSupport._
  import info.whereismyfood.modules.user.CredsJsonSupport._
  implicit val formatter = jsonFormat(OpenOrder.apply, "businessId", "id", "timestamp",
    "client", "contents")
}
