package info.whereismyfood.modules.order

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import boopickle.Default._
import com.google.cloud.datastore.{Entity, EntityValue, FullEntity}
import info.whereismyfood.libs.database.{Databases, DatastoreStorable, KVStorable}
import info.whereismyfood.modules.geo.DeliveryRoute
import info.whereismyfood.modules.order.ProcessedOrder.{OrderStatus, OrderStatuses}
import info.whereismyfood.modules.user.{Creds, UserJson}
import org.slf4j.LoggerFactory
import redis.ByteStringFormatter
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try
/**
  * Created by zakgoichman on 11/8/16.
  */

object ProcessedOrder{
  type OrderStatus = String

  case object OrderStatuses {
    def isReady(o: ProcessedOrder): Boolean = o.status == READY || o.ready || isEnroute(o)
    def isEnroute(o: ProcessedOrder): Boolean = o.status == ENROUTE
    def isOpen(o: ProcessedOrder): Boolean = o.status == OPEN
    def isOngoing(o: ProcessedOrder): Boolean = o.status != DELIVERED

    val OPEN = "open"
    val PREPARING = "preparing"
    val READY = "ready"
    val ENROUTE = "enroute"
    val DELIVERED = "delivered"
  }
  def groupByHaveAddress(orders: Set[ProcessedOrder]) = {
    val grouped = orders.groupBy(_.client.geoaddress.isDefined)
    (grouped.getOrElse(true, Set()), grouped.getOrElse(false, Set()))
  }

  val log = LoggerFactory.getLogger(this.getClass)

  val kind = "ProcessedOrder"
  val _timestamp = "timestamp"
  val _businessId = "businessId"
  val _orderId = "orderId"
  val _items = "items"
  val _clientLatLng = "clientLatLng"
  val _clientPhone = "clientPhone"
  val _status = "status"
  val _courierPhone = "courierPhone"
  val _route = "route"
  val _deliveryMode = "deliveryMode"

  def of(order: Order)(implicit businessId: Long): ProcessedOrder = {
    ProcessedOrder(businessId, order.id, order.timestamp, order.client, order.contents.flatMap(_.toOrderItem), order.deliveryMode)
  }

  implicit val byteStringFormatter = new ByteStringFormatter[ProcessedOrder] {
    override def serialize(data: ProcessedOrder): ByteString = {
      val pickled = Pickle.intoBytes(data)
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
        val res = Databases.inmemory.retrieveSet[String](getSetKey(orders.businessId)).flatMap {
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
        val key = getSetKey(orders.businessId)
        val res = Databases.inmemory.retrieveSet[String](key).flatMap {
          case Seq() =>
            Future.successful(true)
          case existingIds =>
            println("Checking for order ID conflict")
            Future.successful(existingIds.intersect(ids).isEmpty)
        }
        Await.result[Boolean](res, 30 seconds)
      case _ => false
    }
  }

  def save(order: ProcessedOrder*): Boolean = {
    val businessId = order.head.businessId
    if(!(order forall(_.businessId == businessId))) false
    else Try {
      val inmemoryIds = order.map{
        x=>(x.key, x)
      }
      Databases.inmemory.save(30 day, inmemoryIds:_*)
      Databases.inmemory.addToSet(30 day, getSetKey(businessId), inmemoryIds.map(_._1):_*)

      order.par.foreach { o =>
        o.saveToDatastore() match {
          case None => throw new Exception("couldn't save all orders")
          case _ => None
        }
      }
    }.isSuccess
  }

  def saveSingle(order: ProcessedOrder): Boolean = {
    Await.result(Databases.inmemory.save[ProcessedOrder](30 day, (order.key, order)), 30 seconds)
    //TODO: check if redis indeed saves
    true
  }

  def retrieveSingle(businessId: Long, orderId: String): Option[ProcessedOrder] = {
    val name = getKey(businessId, orderId)
    Await.result[Seq[ProcessedOrder]](Databases.inmemory.retrieve[ProcessedOrder](name), 30 seconds).headOption
  }

  def retrieveAllActive(businessId: Long): Seq[ProcessedOrder] = {
    val processedOrders = Databases.inmemory.retrieveSet[String](getSetKey(businessId)).flatMap{
      case Seq() => Future.successful(Seq())
      case orderKeys => Databases.inmemory.retrieve[ProcessedOrder](orderKeys:_*)
    }

    Await.result[Seq[ProcessedOrder]](processedOrders, 15 seconds)
  }

  def mark(businessId: Long, orderId: String): Option[ProcessedOrder] ={
    retrieveSingle(businessId, orderId) match{
      case _order @ Some(order)=>
        saveSingle(order.copyWithReady(!order.ready))
        _order
      case _ => None
    }
  }

  def delete(businessId: Long, orderId: String): Boolean ={
    val setName = getSetKey(businessId)
    val orderName = getKey(businessId, orderId)

    Databases.inmemory.delFromSet(setName, orderName)
    //TODO: check if redis indeed deletes
    true
  }

  def getOrderState(businessId: Long, orderId: String): Option[OrderReady] = {
    retrieveSingle(businessId, orderId) match {
      case Some(order) =>
        Some(OrderReady(order.id, Some(order.ready)))
      case _ =>
        None
    }
  }
}

case class ProcessedOrder(businessId: Long, id: String, timestamp: Long, client: Creds, contents: Seq[OrderItem],
                          deliveryMode: DeliveryMode = DeliveryModes.NA,
                          ready: Boolean = false, courier: Option[UserJson] = None,
                          status: OrderStatus = OrderStatuses.OPEN, route: Option[DeliveryRoute] = None)
    extends DatastoreStorable with KVStorable{
  def demand: Int = contents.size

  import ProcessedOrder._
  def geoid: String = client.geoaddress match {
    case Some(addr) => addr.geoid
    case _ => ""
  }
  override def asDatastoreEntity: Option[FullEntity[_]] = {
    Try {
      val key = datastore.newKeyFactory().setKind(kind).newKey(businessId + id)
      val entity = Entity.newBuilder(key)
        .set(_orderId, id)
        .set(_timestamp, timestamp)
        .set(_businessId, businessId)
        .set(_clientPhone, client.phone)
        .set(_status, status)
        .set(_deliveryMode, deliveryMode.asDatastoreEntity.get)
        .set(_route, route.getOrElse(DeliveryRoute.empty).polyline)
        .set(_items, contents.map(x => new EntityValue(x.asDatastoreEntity(kind).get)).asJava)
      if (client.geoaddress.isDefined)
        entity.set(_clientLatLng, client.geoaddress.get.latLng.toDatastoreLatLng)
      if (courier.isDefined)
        entity.set(_courierPhone, courier.get.phone)

      entity.build()
    }.toOption
  }

  override def key: String = ProcessedOrder.getKey(businessId, id)
  def copyWithReady(ready: Boolean): ProcessedOrder = this.copy(ready = ready, status = OrderStatuses.READY)//, contents = contents.map(_.copy(ready = Some(ready))))
}

object ProcessedOrderJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import OrderItemJsonSupport._
  import info.whereismyfood.modules.geo.DeliveryRouteJsonSupport._
  import info.whereismyfood.modules.user.CredsJsonSupport._
  import info.whereismyfood.modules.user.UserJsonSupport._
  import DeliveryModeJsonSupport._
  implicit val formatter = jsonFormat(ProcessedOrder.apply, "businessId", "id", "timestamp",
    "client", "contents", "deliveryMode", "ready", "courier", "status", "route")
}
