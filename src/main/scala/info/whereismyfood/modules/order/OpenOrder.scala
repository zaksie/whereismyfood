package info.whereismyfood.modules.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.{Entity, EntityValue, FullEntity, PathElement}
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.libs.database.{Databases, DatastoreStorable, KVStorable}
import info.whereismyfood.modules.menu.DishToAdd
import info.whereismyfood.modules.user.{ClientUser, Creds}
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try
/**
  * Created by zakgoichman on 11/8/16.
  */

object OpenOrder{
  def of(businessId: Long, phone: String): Option[OpenOrder] = {
    ClientUser.find(phone) match {
      case Some(user) =>
        Some {
          OpenOrder(businessId,
            System.currentTimeMillis / 1000,
            user.toCreds(),
            DeliveryModes.none
          )
        }
      case _ => None
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass)

  val kind = "OpenOrder"
  val _timestamp = "timestamp"
  val _businessId = "businessId"
  val _orderId = "orderId"
  val _items = "items"
  val _clientPhone = "clientPhone"
  val _deliveryMode = "deliveryMode"

  implicit val byteStringFormatter = new ByteStringFormatter[OpenOrder] {
    override def serialize(data: OpenOrder): ByteString = {
      val pickled = Pickle.intoBytes[OpenOrder](data)
      ByteString(pickled)
    }
    override def deserialize(bs: ByteString): OpenOrder = {
      Unpickle[OpenOrder].fromBytes(bs.asByteBuffer)
    }
  }

  private def getKey(phone: String): String = OpenOrder.kind + "/" + phone

  def save(order: OpenOrder): Boolean = {
    Try {
      Databases.inmemory.save[OpenOrder](MyConfig.getInt("params.days-to-save-order") days, (order.key, order))
      //TODO: throw away if not used. Tho could be used for analyzing incomplete orders
      /*
      Future(order.saveToDatastore() match {
          case None => throw new Exception("couldn't save open order")
          case _ => None
        })
       */
      }.isSuccess
  }

  def delete(order: OpenOrder): Boolean = {
    Try {
      //The order itself is not deleted. it expires.
      Databases.inmemory.delete(order.key)
      //TODO: throw away if not used. Tho could be used for analyzing incomplete orders
      //Future(if(order.removeFromDatastore()) throw new Exception("couldn't remove item from open order"))
    }.isSuccess
  }

  def retrieveBy(phone: String): Option[OpenOrder] = {
    val key = getKey(phone)
    Await.result[Seq[OpenOrder]](Databases.inmemory.retrieve[OpenOrder](key), 30 seconds).headOption
  }

  def addItem(phone: String, item: OrderItem): Boolean = {
    def findUserAndSaveOrder =
      OpenOrder.of(item.businessId, phone) match {
        case Some(order) =>
          order.add(item)
          save(order)
        case _ =>
          false
      }

    retrieveBy(phone) match {
      case Some(order) if order.businessId == item.businessId =>
        save(order.copy(contents = order.contents :+ item.copy(orderId = order.key)))
      case None =>
        findUserAndSaveOrder
      case _ =>
        false
    }
  }

  def removeItem(phone: String, itemId: String): Boolean = {
    retrieveBy(phone) match {
      case Some(order) if order.contents.exists(_.id == itemId) =>
        order.copy(contents = order.contents.filterNot(_.id == itemId)) match {
          case new_order if new_order.contents.size < 1 =>
            delete(new_order)
          case new_order =>
            save(new_order)
        }
      case _ =>
        false
    }
  }

  def remove(phone: String): Unit = {
    val key = getKey(phone)
    Databases.inmemory.delete(key)
  }
}

case class OpenOrder(businessId: Long, timestamp: Long, client: Creds, deliveryMode: String = DeliveryModes.none,
                     var contents: Seq[OrderItem] = Seq())
    extends KVStorable{
  import OpenOrder._
  override def key: String = getKey(client.phone)

  def add(item: OrderItem): Unit = {
    contents = contents :+ item.copy(orderId = key)
  }
}

object OpenOrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.modules.user.CredsJsonSupport._
  import OrderItemJsonSupport._
  implicit val formatter = jsonFormat5(OpenOrder.apply)
}
