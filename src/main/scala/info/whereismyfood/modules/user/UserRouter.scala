package info.whereismyfood.modules.user

import org.slf4j.LoggerFactory

import scala.collection.mutable
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
}
