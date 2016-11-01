package info.whereismyfood.libs.auth

import com.google.appengine.api.datastore.Query.{FilterOperator, FilterPredicate}
import com.google.appengine.api.datastore.{Query}
import info.whereismyfood.libs.database.{DatastoreStorable}

/**
  * Created by zakgoichman on 10/31/16.
  */
object LoginEntity extends DatastoreStorable {
  val kind = "Login"
  override def getFromDatastore[LoginEntity](apikey: String): Option[LoginEntity] = {
    val q = new Query(kind).setFilter(new FilterPredicate("apikey", FilterOperator.EQUAL, apikey))
    val result = datastore.prepare(q).asSingleEntity
    Some(LoginEntity(result.getProperty("name").toString, result.getProperty("role").toString))
  }
}

case class LoginEntity(name: String, role: String)