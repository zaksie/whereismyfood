package info.whereismyfood.libs.auth

import com.google.cloud.datastore.{Entity, Query, QueryResults, ReadOption}
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import info.whereismyfood.libs.database.{Databases, DatastoreFetchable}

/**
  * Created by zakgoichman on 10/31/16.
  */
object LoginEntity extends DatastoreFetchable[LoginEntity] {
  val kind = "Login"
  val propkey_name = "name"
  val propkey_role = "role"

  def getFromDB(param: Any): Option[LoginEntity] = {
    getFromDatastore(param)
  }

  override def getFromDatastore(param: Any): Option[LoginEntity] = {
    val apikey = param.asInstanceOf[String]
    val q: Query[Entity] = Query.newEntityQueryBuilder()
      .setKind(kind)
      .setFilter(PropertyFilter.eq("apikey", apikey))
      .build()
    val result :QueryResults[Entity] = datastore.run(q, ReadOption.eventualConsistency())
    val single = result.next()
    Some(LoginEntity(single.getString(propkey_name), single.getString(propkey_role)))
  }
}

case class LoginEntity(name: String, role: String)