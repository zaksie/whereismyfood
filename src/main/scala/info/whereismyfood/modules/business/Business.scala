package info.whereismyfood.modules.business

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}
import info.whereismyfood.modules.geo.{Address, LatLng}
import info.whereismyfood.modules.user.Roles
import info.whereismyfood.modules.user.Roles.RoleID
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by zakgoichman on 11/11/16.
  */

object Business extends DatastoreFetchable[Business] {
  val log = LoggerFactory.getLogger(this.getClass)
  type JobInBusiness = String
  val kind = "Business"

  object DSTypes {
    val name = "name"
    val address = "address"
    val deliveryModes = "delivery_modes"
    val include = "include"
    val image = "image"
    val rating = "rating"
    val raters = "raters"
    val tagline = "tagline"
    val description = "description"
    val info = "info"
    val owners: JobInBusiness = "owners"
    val couriers: JobInBusiness = "couriers"
    val chefs: JobInBusiness = "chefs"
    val clients: JobInBusiness = "clients"
    val apiers: JobInBusiness = "apiers"
    val none: JobInBusiness = "none"
  }

  def get(ids: Long*): Seq[Business] = {
    println("Getting businesses: " + ids.mkString(","))
    val res = getFromDatastore(ids: _*)
    println(res)
    res
  }

  def apply(entity: Entity): Option[Business] = {
    try {
      import DSTypes._
      val _owners = entity.getList[StringValue](owners).asScala.map(_.get).toSet
      val _couriers = entity.getList[StringValue](couriers).asScala.map(_.get).toSet
      val _chefs = entity.getList[StringValue](chefs).asScala.map(_.get).toSet
      val _apiers = entity.getList[StringValue](apiers).asScala.map(_.get).toSet
      val _info = BusinessInfo.of(entity.getEntity(info)).get

      Some{
        Business(entity.getKey.getId, _info,
          _owners, _couriers, _chefs, _apiers)
      }
    } catch {
      case e: Exception =>
        log.error("Error parsing Business entity", e)
        None
    }
  }

  def fromEntity(entity: Entity): Option[Business] = {
    apply(entity)
  }

  private def createKeys(businessIds: Long*): Seq[Key] = {
    businessIds.map(id => datastore.newKeyFactory().setKind(kind).newKey(id))
  }

  def getFromDatastore(businessIds: Long*): Seq[Business] = {
    val keys = createKeys(businessIds: _*)
    datastore.get(keys: _*).asScala.toSeq.flatMap(Business.fromEntity)
  }

  def getAll: Set[Business] = {
    val q: Query[Entity] = Query.newEntityQueryBuilder()
        .setKind(Business.kind)
        .build()

    datastore.run(q, ReadOption.eventualConsistency())
        .asScala.toSet.flatMap(Business.fromEntity)
  }

  private def getEntitiesFor(phone: String, jobInBusiness: JobInBusiness): Set[Entity] = {
    val q: Query[Entity] = Query.newEntityQueryBuilder()
        .setKind(Business.kind)
        .setFilter(PropertyFilter.eq(jobInBusiness, phone))
        .build()

    datastore.run(q, ReadOption.eventualConsistency())
        .asScala.toSet
  }

  def getIdsFor(phone: String, jobInBusiness: JobInBusiness): Set[Long] = {
    val gql = s"SELECT __key__ FROM ${Business.kind} WHERE $jobInBusiness CONTAINS @userId"
    val q: Query[Key] = Query.newGqlQueryBuilder(Query.ResultType.KEY, gql)
        .setBinding("userId", phone)
        .build()

    datastore.run(q, ReadOption.eventualConsistency())
        .asScala.map(_.getId.toLong).toSet
  }

  def getAllFor(phone: String, jobInBusiness: JobInBusiness): Set[Business] = {
    getEntitiesFor(phone, jobInBusiness).flatMap(Business.fromEntity)
  }

  def removeJobFrom(id: String, businessId: Long, jobInBusiness: JobInBusiness): Boolean = {
    val txn = datastore.newTransaction()
    try {
      val key: Key = createKeys(businessId).head
      val entity = txn.get(key)
      val workers: Set[StringValue] = entity.getList[StringValue](jobInBusiness)
          .asScala.toSet.filter(_.get != id)

      val newEntity = Entity.newBuilder(entity)
          .set(jobInBusiness, workers.toList.asJava)
          .build
      txn.update(newEntity)
      txn.commit()
      if (txn.isActive) {
        txn.rollback()
        false
      }
      else true
    }catch{
      case _: Throwable => false
    }

  }

  def addJobTo(id: String, businessId: Long, jobInBusiness: JobInBusiness): Boolean = {
    val txn = datastore.newTransaction()
    try {
      val addedWorker = StringValue.of(id)
      val key: Key = createKeys(businessId).head
      val entity = txn.get(key)
      val workers: Set[StringValue] = entity.getList[StringValue](jobInBusiness)
          .asScala.toSet + addedWorker

      val newEntity = Entity.newBuilder(entity)
          .set(jobInBusiness, workers.toList.asJava)
          .build
      txn.update(newEntity)
      txn.commit()
      if (txn.isActive) {
        txn.rollback()
        false
      }
      else true
    } catch{
      case _: Throwable =>
        false
    }
  }
}

import Business.{DSTypes => T}

object DeliveryMode{
  def of(entity: FullEntity[_ <: IncompleteKey]): Option[DeliveryMode] = {
    Try {
      DeliveryMode(
        entity.getString(T.name),
        entity.getBoolean(T.include)
      )
    }.toOption
  }
}

case class DeliveryMode(name: String, include: Boolean) extends DatastoreStorable {
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val entity = FullEntity.newBuilder()
      entity.set(T.name, name)
      entity.set(T.include, include)
      entity.build
    }
  }.toOption
}

object BusinessInfo{
  def of(entity: FullEntity[_ <: IncompleteKey]): Option[BusinessInfo] = {
    Try {
      BusinessInfo(
        entity.getString(T.name),
        entity.getString(T.image),
        entity.getList[EntityValue](T.deliveryModes).asScala.map(_.get).map(DeliveryMode.of).map(_.get).toSet, //using 2 maps instead of flatmap to catch errors
        entity.getDouble(T.rating),
        entity.getLong(T.raters).toInt,
        new Address(entity.getEntity(T.address)),
        entity.getString(T.tagline),
        entity.getString(T.description)
      )
    }.toOption
  }
}
case class BusinessInfo(name: String, image: String, deliveryModes: Set[DeliveryMode],
                        rating: Double, raters: Int, address: Address, tagline: String, description: String) extends DatastoreStorable {
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val entity = FullEntity.newBuilder()
      entity.set(T.name, name)
      entity.set(T.image, image)
      entity.set(T.tagline, tagline)
      entity.set(T.description, description)
      entity.set(T.deliveryModes, deliveryModes.map(_.asDatastoreEntity.get).map(EntityValue.of).toList.asJava)
      entity.set(T.address, address.asDatastoreEntity.get)
      entity.set(T.rating, rating)
      entity.set(T.raters, raters)
      entity.build
    }
  }.toOption
}
case class Business(id: Long, info: BusinessInfo, owners: Set[String], couriers: Set[String],
                    chefs: Set[String], apiers: Set[String]) extends DatastoreStorable{
  def filterForRole(role: RoleID): Business = {
    import Roles.api.business._
    val businessRoles = role & Roles.api.business.all
    if((businessRoles & owner_list) != 0)
      this.copy(apiers = Set())
    else if((businessRoles & chef_list) != 0)
      this.copy(owners = Set(), apiers=Set())
    //else if((businessRoles & courier_list) != 0)  - if courier or anything below that, remove all groups
    else this.copy(owners = Set(), apiers=Set(), couriers = Set(), chefs = Set())
  }

  def save(): Boolean = saveToDatastore() match {
    case Some(_) => true
    case _ => false
  }

  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val key = datastore.newKeyFactory()
          .setKind(Business.kind)
          .newKey(id)
      val entity = FullEntity.newBuilder(key)
      entity.set(T.info, info.asDatastoreEntity.get)
      entity.set(T.owners, owners.map(StringValue.of).toList.asJava)
      entity.set(T.couriers, couriers.map(StringValue.of).toList.asJava)
      entity.set(T.chefs, chefs.map(StringValue.of).toList.asJava)
      entity.set(T.apiers, apiers.map(StringValue.of).toList.asJava)
      entity.build
    }
  }.toOption
}

case class BusinessLocation(id: Long, latLng: Option[LatLng] = None)

object BusinessJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.modules.geo.AddressJsonSupport._
  implicit val deliveryModeFormatter = jsonFormat2(DeliveryMode.apply)
  implicit val businessInfoFormatter = jsonFormat8(BusinessInfo.apply)
  implicit val businessFormatter = jsonFormat6(Business.apply)
}
