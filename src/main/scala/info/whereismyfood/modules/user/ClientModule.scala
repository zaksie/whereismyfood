package info.whereismyfood.modules.user

import akka.actor.{Actor, Props}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.Roles.RoleID
import spray.json._

import scala.util.Random
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.payment.{CreditCard, PaymentMethod}
/**
  * Created by zakgoichman on 10/24/16.
  */

object ClientModule {
  case class GetPaymentMethods(cred: Creds)
  case class AddPaymentMethod(method: PaymentMethod, creds: Creds)
  case class PatchPaymentMethod(method: PaymentMethod, creds: Creds)

  case class AddCreditCard(cc: CreditCard, creds: Creds)

  def props = Props[ClientInfoActor]
}

class ClientInfoActor extends UserInfoActor {
  import ClientModule._
  private implicit val executionContext = system.dispatcher

  override protected val job: String = Business.DSTypes.chefs
  override protected val role: RoleID = Roles.chef
  override protected def updateOrCreateInDB = ChefUser.of

  override def receive: Receive = {
    case GetPaymentMethods(creds) =>
      import info.whereismyfood.modules.payment.PaymentMethodJsonSupport._
      ClientUser.find(creds.phone) match {
        case None =>
          sender ! 404
        case Some(user) =>
          PaymentMethod.find(user.ref) match {
            case Seq() => sender ! 404
            case paymentMethods => sender ! paymentMethods.toJson.compactPrint
          }
      }
    case AddCreditCard(cc, creds) =>
      ClientUser.find(creds.phone) match {
        case Some(user) if cc.validate =>
          cc.toPaymentMethod(user) match {
            case Some(paymentMethod) =>
              paymentMethod.saveToDatastore()
              sender ! 200
            case _ => sender ! 500
          }
        case Some(_) => sender ! StatusCodes.NotAcceptable
        case _ => sender ! 400
      }
  }
}



