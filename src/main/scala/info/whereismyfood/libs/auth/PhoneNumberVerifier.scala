package info.whereismyfood.libs.auth

import info.whereismyfood.libs.sms.TwilioClient

/**
  * Created by zakgoichman on 11/2/16.
  */
object PhoneNumberVerifier {
  val CODE_LENGTH = 4
  private def generateCode: String = {
    ("%0"+CODE_LENGTH+"d").format(util.Random.nextInt(Math.pow(10, CODE_LENGTH).toInt))
  }

  private def getBody(code: String):String ={
    "Welcome phone whereismyfood. Your code: " + code
  }

  def sendCode(uuid: String, to: String): Boolean ={
    val code = generateCode
    if(UnverifiedAccount.save(uuid, to, code)) {
      //TwilioClient.send(phone, getBody(code))
      true
    }
    else false
  }

  def checkCode(creds: Creds): Option[DatabaseAccount] = {
    UnverifiedAccount.verify(creds)
  }
}
