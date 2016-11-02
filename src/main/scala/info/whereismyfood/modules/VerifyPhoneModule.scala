package info.whereismyfood.modules

import akka.actor.{Actor, Props}
import akka.util.Timeout
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.auth.{Account, PhoneNumberVerifier, UnverifiedAccount}

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * Created by zakgoichman on 10/24/16.
  */
object VerifyPhoneModule {
  implicit val resolveTimeout = Timeout(300 seconds)
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  def props = Props[VerifyPhoneActor]
  def requestVerificationCode(account: Account) = {
    system.actorSelection("/user/modules/request-verify-phone") ! account
  }

  def verify(account: Account): Boolean = {
    PhoneNumberVerifier.checkCode(account)
  }
}

class VerifyPhoneActor extends Actor {
  override def receive: Receive = {
    case account: Account => {
      PhoneNumberVerifier.sendCode(account.uuid, account.to)
    }
  }
}
