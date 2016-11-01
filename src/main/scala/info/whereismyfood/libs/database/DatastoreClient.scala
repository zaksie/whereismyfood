package info.whereismyfood.libs.database

import com.google.cloud.datastore._
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 10/23/16.
  */
class DatastoreEngine private extends DatabaseImplementation {
  val log = LoggerFactory.getLogger(this.getClass)
  val datastore: Datastore = DatastoreOptions.defaultInstance.service
  private val keyFactory = datastore.newKeyFactory.kind("Task")

  def executeQuery(gql: String): QueryResults[Entity] = {
    val query = Query.gqlQueryBuilder(Query.ResultType.ENTITY, gql).allowLiteral(true).build()
    try{
      datastore.run(query, ReadOption.eventualConsistency)
    }catch{
      case e: Exception => {
        log.error("Encountered error", e)
        throw e
      }
    }
  }
}

object DatastoreEngine{
  val instance = new DatastoreEngine
}

