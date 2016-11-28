package info.whereismyfood.modules.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.DatastoreFetchable
import info.whereismyfood.modules.geo.Address
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._

/**
  * Created by zakgoichman on 11/11/16.
  */

object Business extends DatastoreFetchable[Business] {
  type JobInBusiness = String
  val kind = "Business"
  object Jobs {
    val name = "name"
    val address = "address"
    val owners: JobInBusiness  = "owners"
    val couriers: JobInBusiness  = "couriers"
    val chefs: JobInBusiness = "chefs"
    val apiers: JobInBusiness = "apiers"
    val none: JobInBusiness = "none"
  }

  def get(ids: Long*): Seq[Business] = {
    getFromDatastore(ids:_*)
  }

  def apply(entity: Entity): Option[Business] = {
    try {
      val owners = entity.getList[StringValue](Jobs.owners).asScala.map(_.get).toSet
      val couriers = entity.getList[StringValue](Jobs.couriers).asScala.map(_.get).toSet
      val chefs = entity.getList[StringValue](Jobs.chefs).asScala.map(_.get).toSet
      val apiers = entity.getList[StringValue](Jobs.apiers).asScala.map(_.get).toSet
      Some {
        Business(entity.getKey.getId,
          entity.getString(Jobs.name),
          new Address(entity.getEntity(Jobs.address)),
          owners, couriers, chefs, apiers)
      }
    } catch {
      case e: Exception =>
        println(e)
        None
    }
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

  private def getEntitiesFor(phone: String, jobInBusiness: JobInBusiness): Set[Entity]={
    val q: Query[Entity] = Query.newEntityQueryBuilder()
      .setKind(Business.kind)
      .setFilter(PropertyFilter.eq(jobInBusiness, phone))
      .build()

    datastore.run(q, ReadOption.eventualConsistency())
      .asScala.toSet
  }

  def getIdsFor(phone: String, jobInBusiness: JobInBusiness): Set[Long] = {
    getEntitiesFor(phone, jobInBusiness).map(_.getKey.getId.toLong)
  }
  def getAllFor(phone: String, jobInBusiness: JobInBusiness): Set[Business]={
    getEntitiesFor(phone, jobInBusiness).flatMap(Business.fromEntity)
  }

  def removeJobFrom(id: String, businessId: Long, jobInBusiness: JobInBusiness): Boolean = {
    val txn = datastore.newTransaction()
    try {
      val key: Key = createKeys(businessId).head
      val entity = txn.get(key)
      val couriers: Set[StringValue] = entity.getList[StringValue](jobInBusiness)
        .asScala.toSet.filter(_.get != id)

      val newEntity = Entity.newBuilder(entity)
        .set(jobInBusiness, couriers.toList.asJava)
        .build
      txn.update(newEntity)
      txn.commit()
      true
    } finally {
      txn.isActive() match {
        case true =>
          txn.rollback()
          false
        case _ => true
      }
    }
  }

  def addJobTo(id: String, businessId: Long, jobInBusiness: JobInBusiness): Boolean = {
    addToDatastore(id, businessId, jobInBusiness)
  }

  def addToDatastore(id: String, businessId: Long, jobInBusiness: JobInBusiness): Boolean = {
    val txn = datastore.newTransaction()
    try {
      val addedCourier = StringValue.of(id)
      val key: Key = createKeys(businessId).head
      val entity = txn.get(key)
      val couriers: Set[StringValue] = entity.getList[StringValue](jobInBusiness)
        .asScala.toSet + addedCourier

      val newEntity = Entity.newBuilder(entity)
        .set(jobInBusiness, couriers.toList.asJava)
        .build
      txn.update(newEntity)
      txn.commit()
      true
    } finally {
      txn.isActive() match {
        case true =>
          txn.rollback()
          false
        case _ => true
      }
    }
  }
}


case class Business(id: Long, name: String, address: Address, owners: Set[String], couriers: Set[String],
                    chefs: Set[String], apiers: Set[String], config: BusinessConfig = BusinessConfig.default)



trait BusinessJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import Address._
  import BusinessConfig._
  implicit val formatter2 = jsonFormat8(Business.apply)
}
