package info.whereismyfood.libs.auth

import com.google.appengine.api.datastore.Query.{FilterOperator, FilterPredicate}
import com.google.appengine.api.datastore.{Query}
import info.whereismyfood.libs.database.{DatastoreStorable}

/**
  * Created by zakgoichman on 10/31/16.
  */
object LoginEntity extends DatastoreStorable[LoginEntity] {
  type DTYPE = LoginEntity
  val kind = "Login"
  override def getFromDatastore(apikey: String): Option[LoginEntity] = {
    val q = new Query(kind).setFilter(new FilterPredicate("apikey", FilterOperator.EQUAL, apikey))
    val result = datastore.prepare(q).asSingleEntity
    Some(LoginEntity(result.getProperty("name").toString, result.getProperty("role").toString))
  }

  override def saveToDatastore: Unit = throw new NotImplementedError()
}

case class LoginEntity(name: String, role: String)