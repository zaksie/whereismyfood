package info.whereismyfood.modules.user

import akka.actor.{Actor, Props}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID

import scala.concurrent.{Await, Future}
import scala.util.Random

import info.whereismyfood.aux.ActorSystemContainer.Implicits._
/**
  * Created by zakgoichman on 10/24/16.
  */

object ChefModule {
  case object RequestToken
  case class UpdateChef(userJson: UserJson)
  def props = Props[ChefActor]
}

class ChefActor extends UserActor {
  import ChefModule._
  private implicit val executionContext = system.dispatcher

  override protected val job: String = Business.DSTypes.chefs
  override protected val role: RoleID = Roles.chef
  override protected def updateOrCreateInDB = ChefUser.of

  private def length = Random.nextInt(2) + 4

  override def receive: Receive = {
    case AddUser(userJson) =>
      sender ! updateOrCreateUser(userJson)
    case RequestToken =>
      val s = sender
      allocId().map{ id =>
          println(s"Got transactional id: $id")
          s ! Some(id)
      }
    case UpdateChef(_user) =>
      val user = _user.copy(prevPhone = Some(_user.phone))
      val s = sender
      //TODO: This doesn't belong here. use it when chef is verified
      //ChefUser.removeIfTransactional(user.phone).map {
      //  case true =>
      //    println("Removed transactional " + user.phone)
      ChefUser.isTransactional(user.phone).map {
        case true =>
          s ! updateOrCreateUser(user)
        case _ =>
          s ! updateUser(user)
      }
  }

  private def allocId(): Future[String] = {
    val id = "CHEF-" + Random.alphanumeric.take(length).mkString.toUpperCase
    ChefUser.createTransactionalUser(id).flatMap {
      case Some(_) =>
        Future.successful(id)
      case _ =>
        allocId()
    }
  }
}



