package info.whereismyfood.modules.user

import info.whereismyfood.libs.database.Databases
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 11/24/16.
  */
object UserRouter {
  private val log = LoggerFactory.getLogger(this.getClass)

  private var userKinds = mutable.Set[GenericUserTrait[_ <: GenericUser]]()
  def addUserCompanionObject(obj: GenericUserTrait[_ <: GenericUser]): Unit = {
    userKinds += obj
  }

  def getByJob(job: String): Option[GenericUserTrait[_ <: GenericUser]] = {
    userKinds.find(_.jobInBusiness == job)
  }

  def getJwtFor(id: String): Option[String] = {
    Await.result[Option[String]](Databases.inmemory.retrieve(s"jwt/$id"), 5 seconds)
  }
}
