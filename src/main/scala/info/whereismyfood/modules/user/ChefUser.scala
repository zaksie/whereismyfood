package info.whereismyfood.modules.user

import com.google.cloud.datastore.FullEntity.Builder
import com.google.cloud.datastore.{Entity, Key, StringValue}
import info.whereismyfood.aux.ActorSystemContainer.Implicits.system
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * Created by zakgoichman on 11/18/16.
  */


object ChefUser extends GenericUserTrait[ChefUser]{
  private implicit val executionContext = system.dispatcher

  override def role: RoleID = Roles.chef
  override def of(creds: Creds) = ChefUser(creds)
  override def find(phone: String): Option[ChefUser] = {
    super.find(phone) match {
      case Some(user) =>
        Some(user)
      case _ => None
    }
  }
  def jobInBusiness: Business.JobInBusiness = Business.DSTypes.chefs
  override protected def userActorFactory = Some(ChefUserActor)
  def createTransactionalUser(id: String): Future[Option[String]] = {
    import transactionalUser._
    isTransactional(id).flatMap{
      case false =>
        if(find(id).isDefined) Future.successful(None)
        else Databases.inmemory.save[String](expiry, (prefix + id, ""))
            .flatMap(_=>Future(Some(id)))
      case _ => Future.successful(None)
    }
  }
  def removeIfTransactional(id: String): Future[Boolean] ={
    import transactionalUser._
    isTransactional(id).flatMap{
      case true =>
        Databases.inmemory.delete(prefix + id)
            .flatMap(_=>Future.successful(true))
      case _ => Future.successful(false)
    }
  }
  def isTransactional(id: String): Future[Boolean] ={
    import transactionalUser._
    Databases.inmemory.retrieve(prefix + id).map(_.isDefined)
  }
}

final case class ChefUser(override val creds: Creds) extends GenericUser(creds) {
  private implicit val executionContext = system.dispatcher

  override def compobj = ChefUser

  override def _copy = copy _

  override def getOTPBody(code: String*): String = ???

  override def extendDatastoreEntity(entity: Builder[Key]): Unit = {}

  override def extendFromDatastore(entity: Entity): this.type = this

  def isTransactionalExpired: Boolean = {
    import transactionalUser._
    println(s"Checking if $phone is transactional")
    Await.result[Boolean](Databases.inmemory.retrieve(prefix + phone).map(_.isEmpty), 10 seconds)
  }

  def getTransactionalExpiry: Option[Long] = {
    import transactionalUser._
    Await.result[Option[Long]](Databases.inmemory.redis.ttl(prefix + phone)
        .map(x=> if(x < 0) None else Some(x*1000 /*expiry saved in seconds*/)), 10 seconds)
  }
}