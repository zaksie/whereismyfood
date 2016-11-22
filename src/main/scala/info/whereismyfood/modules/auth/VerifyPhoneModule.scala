package info.whereismyfood.modules.auth

import akka.actor.{Actor, Props}
import info.whereismyfood.libs.sms.TwilioClient


/**
  * Created by zakgoichman on 10/24/16.
  */

case class RequestOTP(phone: String, body: String)

object VerifyPhoneModule {
  def props = Props[VerifyPhoneActor]
}

class VerifyPhoneActor extends Actor {
  override def receive: Receive = {
    case RequestOTP(phone, body) => TwilioClient.send(phone, body)
  }
}