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
    "Welcome to whereismyfood. Your code: " + code
  }

  def sendCode(uuid: String, to: String): Unit ={
    val code = generateCode
    UnverifiedAccountCompanion.save(uuid, to, code)
    TwilioClient.send(to, getBody(code))
  }

  def checkCode(account: Account): Boolean = {
    new UnverifiedAccount(account).isValid
  }
}
