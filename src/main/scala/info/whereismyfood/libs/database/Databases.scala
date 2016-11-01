package info.whereismyfood.libs.database

/**
  * Created by zakgoichman on 10/23/16.
  */
object Database {
  val instance = DatastoreEngine.instance
}

trait DatabaseImplementation {
}