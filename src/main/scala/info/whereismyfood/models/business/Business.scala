package info.whereismyfood.models.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.StructuredQuery.{PropertyFilter}
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{DatastoreFetchable}
import info.whereismyfood.libs.geo.Address
import info.whereismyfood.models.user.{Creds}
import spray.json.DefaultJsonProtocol

import collection.JavaConverters._
import scala.util.Try

/**
  * Created by zakgoichman on 11/11/16.
  */

object Business extends DatastoreFetchable[Business] {
  val kind = "Business"
  val _name = "name"
  val _address = "address"
  val _owners = "owners"
  val _couriers = "couriers"

  def get(ids: Long*): Seq[Business] = {
    getFromDatastore(ids:_*)
  }

  def apply(entity: Entity): Option[Business] = {
    Try {
      val owners = entity.getList[StringValue](_owners).asScala.map(_.get).toSet
      val couriers = entity.getList[StringValue](_couriers).asScala.map(_.get).toSet
      Business(entity.getKey.getId,
        entity.getString(_name),
        new Address(entity.getEntity(_address)),
        owners, couriers)
    }.toOption
  }

  def fromEntity(entity:Entity): Option[Business] = {
    apply(entity)
  }

  private def createKeys(businessIds: Long*): Seq[Key] = {
    businessIds.map(id => datastore.newKeyFactory().setKind(kind).newKey(id))
  }

  def getFromDatastore(businessIds: Long*): Seq[Business] = {
    val keys = createKeys(businessIds:_*)
    datastore.get(keys: _*).asScala.toSeq.flatMap(Business.fromEntity)
  }

  def getAll: Set[Business] = {
    val q: Query[Entity] = Query.newEntityQueryBuilder()
      .setKind(Business.kind)
      .build()

    datastore.run(q, ReadOption.eventualConsistency())
      .asScala.toSet.flatMap(Business.fromEntity)
  }

  private def getEntitiesFor(phone: String): Set[Entity]={
    val q: Query[Entity] = Query.newEntityQueryBuilder()
      .setKind(Business.kind)
      .setFilter(PropertyFilter.eq(Business._owners, phone))
      .build()

    datastore.run(q, ReadOption.eventualConsistency())
      .asScala.toSet
  }

  def getIdsFor(phone: String): Set[Long] = {
    getEntitiesFor(phone).map(_.getKey.getId.toLong)
  }
  def getAllFor(phone: String): Set[Business]={
    getEntitiesFor(phone).flatMap(Business.fromEntity)
  }

  def addCourierTo(courierId: String, businessId: Long): Boolean = {
    addToDatastore(courierId, businessId)
  }

  def addToDatastore(courierId: String, businessId: Long): Boolean = {
    Try {
      val addedCourier = StringValue.of(courierId)
      val key: Key = createKeys(businessId).head
      val entity = datastore.get(key, ReadOption.eventualConsistency)
      val couriers: Set[StringValue] = entity.getList[StringValue](_couriers)
        .asScala.toSet + addedCourier

      val newEntity = Entity.newBuilder(entity)
        .set(_couriers, couriers.toList.asJava)
        .build
      datastore.update(newEntity)
    }.isSuccess
  }
}


case class Business(id: Long, name: String, address: Address, owners: Set[String], couriers: Set[String], config: BusinessConfig = BusinessConfig.default)



trait BusinessJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import Address._
  import BusinessConfig._
  implicit val formatter2 = jsonFormat6(Business.apply)
}
