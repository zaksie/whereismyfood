package info.whereismyfood.models.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.{EntityValue, FullEntity}
import info.whereismyfood.libs.database.{Databases, DatastoreStorable, KVStorable}
import info.whereismyfood.models.geo.DeliveryRoute
import info.whereismyfood.models.user.{CourierJson, Creds}
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import collection.JavaConverters._
/**
  * Created by zakgoichman on 11/8/16.
  */

object ProcessedOrder{
  val log = LoggerFactory.getLogger(this.getClass)

  val kind = "ProcessedOrder"
  val _timestamp = "timestamp"
  val _businessId = "businessId"
  val _orderId = "orderId"
  val _courier = "courier"
  val _items = "items"
  val _clientLatLng = "clientLatLng"
  val _clientPhone = "clientPhone"

  def of(order: Order)(implicit businessId: Long): ProcessedOrder = {
    ProcessedOrder(businessId, order.id, order.timestamp, order.client, order.contents)
  }

  implicit val byteStringFormatter = new ByteStringFormatter[ProcessedOrder] {
    override def serialize(data: ProcessedOrder): ByteString = {
      val pickled = Pickle.intoBytes[ProcessedOrder](data)
      ByteString(pickled)
    }
    override def deserialize(bs: ByteString): ProcessedOrder = {
      Unpickle[ProcessedOrder].fromBytes(bs.asByteBuffer)
    }
  }

  private def getSetKey(businessId: Long): String = ProcessedOrder.kind + "Set-" + businessId
  private def getKey(businessId: Long, orderId: String): String = ProcessedOrder.kind + "/" + businessId + "/" + orderId

  def validTransformedIdsInBatch(orders: Orders): Option[Seq[String]] = {
    val orderIds = orders.orders.map(x => getKey(orders.businessId, x.id))
    if(orderIds.distinct.size == orderIds.size)
      Some(orderIds)
    else None
  }

  def noIdsUnique(orders: Orders): Boolean = {
    validTransformedIdsInBatch(orders) match {
      case Some(ids) =>
        val res = Databases.inmemory.retrieveSet(getSetKey(orders.businessId)).flatMap {
          case Seq() => Future.successful(false)
          case existingIds => Future.successful(existingIds.intersect(ids).size == ids.size)
        }
        Await.result[Boolean](res, 30 seconds)
      case _ => false
    }
  }

  def allIdsUnique(orders: Orders): Boolean = {
    validTransformedIdsInBatch(orders) match {
      case Some(ids) =>
        val res = Databases.inmemory.retrieveSet(getSetKey(orders.businessId)).flatMap {
          case Seq() => Future.successful(true)
          case existingIds =>
            Future.successful(existingIds.intersect(ids).isEmpty)
        }
        Await.result[Boolean](res, 30 seconds)
      case _ => false
    }
  }

  def save(businessId: Long, order: ProcessedOrder*): Boolean = {
    //TODO: assuming businessIds == order.businessIds
    Try {
      val inmemoryIds = order.map{
        x=>(x.key, x)
      }
      Databases.inmemory.save(30 day, inmemoryIds:_*)
      Databases.inmemory.addToSet(30 day, getSetKey(businessId), inmemoryIds.map(_._1):_*)

      order.par.foreach { o =>
        o.saveToDatastore match {
          case None => throw new Exception("couldn't save all orders")
          case _ => None
        }
      }
    }.isSuccess
  }

  def saveSingle(order: ProcessedOrder): Boolean = {
    Await.result(Databases.inmemory.save(30 day, (order.key, order)), 30 seconds)
    //TODO: check if redis indeed saves
    true
  }

  def retrieveSingle(businessId: Long, orderId: String): Option[ProcessedOrder] = {
    val name = getKey(businessId, orderId)
    Await.result[Seq[ProcessedOrder]](Databases.inmemory.retrieve[ProcessedOrder](name), 30 seconds).headOption
  }

  def retrieveAllActive(businessId: Long): Seq[ProcessedOrder] = {
    val processedOrders = Databases.inmemory.retrieveSet(getSetKey(businessId)).flatMap{
      case Seq() => Future.successful(Seq())
      case orderKeys => Databases.inmemory.retrieve[ProcessedOrder](orderKeys:_*)
    }

    Await.result[Seq[ProcessedOrder]](processedOrders, 30 seconds)
  }

  def mark(businessId: Long, orderId: String, ready: Boolean): Boolean ={
    retrieveSingle(businessId, orderId) match{
      case Some(order)=>
        saveSingle(order.copy(ready = ready))
      case _ => false
    }
  }

  def delete(businessId: Long, orderId: String): Boolean ={
    val setName = getSetKey(businessId)
    val orderName = getKey(businessId, orderId)

    Databases.inmemory.delFromSet(setName, orderName)
    //TODO: check if redis indeed deletes
    true
  }
}

case class ProcessedOrder(businessId: Long, id: String, timestamp: Long, client: Creds, contents: Seq[OrderItem],
                          ready: Boolean = false, courier: Option[CourierJson] = None,
                          route: Option[DeliveryRoute] = None) extends DatastoreStorable with KVStorable{
  import ProcessedOrder._

  override def asDatastoreEntity: Option[FullEntity[_]] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey()
    val entity = FullEntity.newBuilder(key)
    entity.set(_orderId, id)
    entity.set(_timestamp, timestamp)
    entity.set(_businessId, businessId)
    entity.set(_clientPhone, client.phone)
    if(client.geoaddress.isDefined)
        entity.set(_clientLatLng, client.geoaddress.get.latLng.toDatastoreLatLng)
    //TODO: add courier & route
    entity.set(_items, contents.map(x => new EntityValue(x.asDatastoreEntity.get)).asJava)
    Option(entity.build())
  }

  override def key: String = ProcessedOrder.getKey(businessId, id)
}

object ProcessedOrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.models.user.CredsJsonSupport._
  import OrderJsonSupport._
  import info.whereismyfood.models.geo.DeliveryRouteJsonSupport._
  implicit val courierJsonFormatter = jsonFormat(CourierJson.apply, "name", "phone", "image", "vehicleType")
  implicit val formatter = jsonFormat(ProcessedOrder.apply, "businessId", "id", "timestamp",
    "client", "contents", "ready", "courier", "route")
}
