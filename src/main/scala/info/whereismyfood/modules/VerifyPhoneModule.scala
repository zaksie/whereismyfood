package info.whereismyfood.modules

import akka.pattern.ask
import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.google.gson.Gson
import info.whereismyfood.aux.ActorSystemContainer
import info.whereismyfood.libs.auth.{Creds, DatabaseAccount, PhoneNumberVerifier, UnverifiedAccount}

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * Created by zakgoichman on 10/24/16.
  */
object VerifyPhoneModule {
  implicit val resolveTimeout = Timeout(30 seconds)
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer

  def props = Props[VerifyPhoneActor]
  def requestVerificationCode(creds: Creds): Boolean = {
    val actorRef = Await.result(system.actorSelection("/user/modules/request-verify-phone").resolveOne(), resolveTimeout.duration)
    Await.result(actorRef ? creds, resolveTimeout.duration).asInstanceOf[Boolean]
  }

  def verify(creds: Creds): VerificationResult = {
    VerificationResult(PhoneNumberVerifier.checkCode(creds))
  }
}

class VerifyPhoneActor extends Actor {
  override def receive: Receive = {
    case creds: Creds => {
      sender ! PhoneNumberVerifier.sendCode(creds.uuid, creds.phone)
    }
  }
}

object VerificationResult {
  val gson = new Gson
  object Result{
    val invalid = Result(ok = false)
  }

  final case class Result(ok: Boolean, jwt: String = "")

}
final case class VerificationResult(account: Option[DatabaseAccount]){
  import VerificationResult._

  def ok = account.isDefined
  def toJson = account match {
    case None => gson.toJson(Result.invalid)
    case Some(a) => gson.toJson(Result(ok = true, a.jwt))
  }
}