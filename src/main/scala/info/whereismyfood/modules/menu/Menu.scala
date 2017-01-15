package info.whereismyfood.modules.menu

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by zakgoichman on 11/11/16.
  */

object Menu extends Common[Menu] {
  override val kind = "Menu"

  object FieldNames {
    val _type = "type"
    val sections = "sections"
    val title = "title"
    val image = "image"
    val active = "active"
    val subtitle = "subtitle"
    val menuDishes = "menuDishes"
    val businesses = "businesses"
    val discountPrice = "discountPrice"
    val dishId = "dishId"
    val mondayBasedDayIndex = "mondayBasedDayIndex"
    val hours = "hours"
    val name = "name"
    val include = "include"
    val days = "days"
    val schedule = "schedule"
  }

  override def of(entity: Entity): Option[Menu] = {
    Try {
      import FieldNames._
      Menu(entity.getKey.getId,
        entity.getList[LongValue](businesses).asScala.map(_.get.toLong),
        entity.getString(title),
        entity.getString(image),
        entity.getBoolean(active),
        entity.getList[EntityValue](sections).asScala.map(_.get).flatMap(Section.of),
        MenuSchedule.of(entity.getEntity(schedule)).get)
    }.toOption
  }
}
object Section extends DatastoreFetchable[Section]{
  def of(entity: FullEntity[_ <: IncompleteKey]): Option[Section] = {
    Try {
      import Menu.FieldNames._
      Section(
        entity.getString(title),
        entity.getString(subtitle),
        entity.getList[EntityValue](menuDishes).asScala.map(_.get).flatMap(MenuDish.of)
      )
    }.toOption
  }
}
object MenuDish extends DatastoreFetchable[MenuDish]{
  def of(entity: FullEntity[_]): Option[MenuDish] = {
    Try {
      import Menu.FieldNames._
      MenuDish(
        entity.getLong(dishId),
        Price.of(entity.getEntity(discountPrice))
      )
    }.toOption
  }
}
object MenuScheduleDay extends DatastoreFetchable[MenuScheduleDay]{
  def of(entity: FullEntity[_ <: IncompleteKey]): Option[MenuScheduleDay] = {
    Try {
      import Menu.FieldNames._
      MenuScheduleDay(
        entity.getLong(mondayBasedDayIndex).toInt,
        entity.getString(hours),
        entity.getString(name),
        entity.getBoolean(include)
      )
    }.toOption
  }
}
object MenuSchedule extends DatastoreFetchable[MenuSchedule]{
  def of(entity: FullEntity[_ <: IncompleteKey]): Option[MenuSchedule] = {
    Try {
      import Menu.FieldNames._
      MenuSchedule(
        entity.getString(_type),
        entity.getString(hours),
        entity.getList[EntityValue](days).asScala.map(_.get).flatMap(MenuScheduleDay.of))
    }.toOption
  }
}
case class MenuDish(dishId: Long, discountPrice: Price) extends DatastoreStorable{
  import Menu._
  val kind = "Menu/Section/MenuDish"
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val key = datastore.newKeyFactory().setKind(kind).newKey()
      FullEntity.newBuilder(key)
          .set(FieldNames.dishId, dishId)
          .set(FieldNames.discountPrice, discountPrice.asDatastoreEntity.get)
          .build
    }.toOption
  }
}
case class Section(title: String, subtitle: String, dishes: Seq[MenuDish]) extends DatastoreStorable{
  import Menu._
  val kind = "Menu/Section"
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val key = datastore.newKeyFactory().setKind(kind).newKey()
      FullEntity.newBuilder(key)
          .set(FieldNames.title, title)
          .set(FieldNames.subtitle, subtitle)
          .set(FieldNames.menuDishes, dishes.map(_.asDatastoreEntity.get).map(EntityValue.of).asJava)
          .build
    }.toOption
  }
}
case class MenuScheduleDay(mondayBasedDayIndex: Int, hours: String, name: String, include: Boolean) extends DatastoreStorable{
  import Menu._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val key = datastore.newKeyFactory().setKind(kind).newKey()
      FullEntity.newBuilder(key)
          .set(FieldNames.mondayBasedDayIndex, mondayBasedDayIndex)
          .set(FieldNames.hours, hours)
          .set(FieldNames.name, name)
          .set(FieldNames.include, include)
          .build
    }.toOption
  }
}
case class MenuSchedule(`type`: String, hours: String, days: Seq[MenuScheduleDay]) extends DatastoreStorable{
  import Menu._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val key = datastore.newKeyFactory().setKind(kind).newKey()
      FullEntity.newBuilder(key)
          .set(FieldNames._type, `type`)
          .set(FieldNames.hours, hours)
          .set(FieldNames.days, days.map(_.asDatastoreEntity.get).map(EntityValue.of).asJava)
          .build
    }.toOption
  }
}
case class Menu(id: Long, businessIds: Seq[Long], title: String,
                image: String, active: Boolean, sections: Seq[Section],
                schedule: MenuSchedule) extends DatastoreStorable{
  import Menu._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val __image = if(id > 0 && image == "") getFromDatastore(id).get.image else image
      val key = datastore.newKeyFactory().setKind(kind)
      FullEntity.newBuilder(if(id > 0) key.newKey(id) else key.newKey())
          .set(FieldNames.businesses, businessIds.map(LongValue.of).asJava)
          .set(FieldNames.title, title)
          .set(FieldNames.image, __image)
          .set(FieldNames.active, active)
          .set(FieldNames.sections, sections.map(_.asDatastoreEntity.get).map(EntityValue.of).asJava)
          .set(FieldNames.schedule, schedule.asDatastoreEntity.get)
          .build
    }.toOption
  }
}

object MenuJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import PriceJsonSupport._
  implicit val menuDishFormatter = jsonFormat(MenuDish.apply, "dishId", "discountPrice")
  implicit val menuScheduleDayFormatter = jsonFormat4(MenuScheduleDay.apply)
  implicit val menuScheduleFormatter = jsonFormat3(MenuSchedule.apply)
  implicit val sectionFormatter = jsonFormat(Section.apply,"title", "subtitle", "dishes")
  implicit val menuFormatter = jsonFormat7(Menu.apply)
}
