package info.whereismyfood.modules.menu

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.DatastoreFetchable
import info.whereismyfood.modules.business.Business
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.collection.JavaConverters._

/**
  * Created by zakgoichman on 11/11/16.
  */

object Menu extends DatastoreFetchable[Menu] {
  private val log = LoggerFactory.getLogger(this.getClass)
  val kind = "Menu"
  private val MENU_KEY = "main_menu"

  object DSTypes {
    val sections = "sections"
    val title = "title"
    val price = "price"
    val dishes = "dishes"
  }

  def apply(entity: Entity): Option[Menu] = {
    try {
      import DSTypes._
      val businessId = entity.getKey.getAncestors.asScala.head.getId
      val _sections: Seq[Section] = entity.getList[EntityValue](sections).asScala
          .map(_.get).map { x =>
        val _dishes = x.getList[LongValue](dishes).asScala.flatMap(dishId => Dish.getFromDatastore(businessId, dishId.get))
        Section(x.getString(title), x.getString(price), _dishes)
      }
      Some(Menu(
        businessId,
        entity.getString(title),
        _sections
      ))
    } catch {
      case e: Exception =>
        log.error("Error parsing Business entity", e)
        None
    }
  }

  private def createKey(businessId: Long): Key = {
    val ancestor = PathElement.of(Business.kind, businessId)
    datastore.newKeyFactory().setKind(kind).addAncestor(ancestor).newKey(MENU_KEY)
  }

  def get(businessId: Long): Option[Menu] = {
    try {
      val q: Query[Entity] = Query.newEntityQueryBuilder()
          .setKind(kind)
          .setFilter(PropertyFilter.hasAncestor(
            datastore.newKeyFactory().setKind(Business.kind).newKey(businessId)))
          .build

      Menu.apply(datastore.run(q, ReadOption.eventualConsistency())
          .asScala.next)
    }catch{
      case e: Throwable =>
        log.error("Failed to get menus for business " + businessId)
        None
    }
  }

  def addToDatastore(businessId: Long, menu: Menu): Boolean = {
    import DSTypes._
    try {
      val sectionEntities = menu.sections.map { x =>
        val ancestor = PathElement.of(kind, MENU_KEY)
        val key: IncompleteKey = datastore.newKeyFactory().setKind(Section.kind).addAncestor(ancestor).newKey()
        val entity = FullEntity.newBuilder(key)
            .set(title, x.title)
            .set(price, x.price)
            .set(dishes, x.dishes.map(y=>LongValue.of(y.id)).toList.asJava)
            .build
        EntityValue.of(entity)
      }.toList.asJava
      val key: Key = createKey(businessId)
      val entity = Entity.newBuilder(key)
          .set(sections, sectionEntities)
          .build
      datastore.put(entity)
      true
    } catch{
      case _: Throwable => false
    }
  }
}
object Section{
  val kind = "Section"
}
case class Section(title: String, price: String, dishes: Seq[Dish])
case class Menu(businessId: Long, title: String, sections: Seq[Section])

object MenuJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import info.whereismyfood.modules.menu.DishJsonSupport._
  implicit val sectionFormatter = jsonFormat3(Section.apply)
  implicit val menuFormatter = jsonFormat3(Menu.apply)
}
