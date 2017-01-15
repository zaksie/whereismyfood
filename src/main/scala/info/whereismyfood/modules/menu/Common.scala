package info.whereismyfood.modules.menu

import com.google.cloud.datastore.{Entity, Key, Query, ReadOption}
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.util.Try
/**
  * Created by zakgoichman on 1/14/17.
  */
trait Common[T <: DatastoreStorable] extends DatastoreFetchable[T]{
  protected val log: Logger = LoggerFactory.getLogger(getClass.getName)
  val kind: String
  def of(entity: Entity): Option[T]
  def getFromDatastore(id: Long): Option[T] = {
    val key = datastore.newKeyFactory().setKind(kind).newKey(id)
    Try {
      of {
        datastore.get(key, ReadOption.eventualConsistency)
      }.get
    }.toOption
  }


  def find(id: Long): Option[T] = {
    getFromDatastore(id)
  }

  def addOrChange(item: T): Boolean = {
    log.info("Saving dish to datastore")
    try {
      datastore.put(item.asDatastoreEntity.get)
      true
    } catch {
      case e: Throwable =>
        log.error("Failed to add or change dish", e)
        false
    }
  }

  def remove(dishId: Long): Boolean = {
    log.info("Removing dish from datastore")
    try {
      val key = datastore.newKeyFactory().setKind(kind).newKey(dishId)
      datastore.delete(key)
      true
    } catch {
      case e: Throwable =>
        log.error("Failed to remove dish", e)
        false
    }
  }
  def getRecordsByBusinessId(businessId: Long): Seq[T] = {
    try {
      val q = runGql("*", businessId).asInstanceOf[Query[Entity]]
      val records = datastore.run(q, ReadOption.eventualConsistency()).asScala
      records.flatMap(of).toSeq
    } catch {
      case _: Throwable =>
        log.error("Failed to get menus for business " + businessId)
        Seq()
    }
  }

  def getKeysByBusinessId(businessId: Long): Seq[Long] = {
    try {
      val q = runGql("__key__", businessId).asInstanceOf[Query[Key]]
      datastore.run(q, ReadOption.eventualConsistency()).asScala.map(_.getId.toLong).toSeq
    } catch {
      case _: Throwable =>
        log.error("Failed to get menus for business " + businessId)
        Seq()
    }
  }

  private def runGql(what: String, businessId: Long): Query[_] ={
    val gql = s"SELECT $what FROM $kind WHERE businesses CONTAINS @id"
    val t = if (what == "__key__") Query.ResultType.KEY else Query.ResultType.ENTITY
    Query.newGqlQueryBuilder(t, gql)
        .setBinding("id", businessId)
        .build
  }
}
