package info.whereismyfood.libs.sms

import com.twilio.sdk.Twilio
import com.twilio.sdk.`type`.PhoneNumber
import com.twilio.sdk.resource.api.v2010.account.Message
import info.whereismyfood.aux.MyConfig
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 11/2/16.
  */
object TwilioClient {
  private val log = LoggerFactory.getLogger(this.getClass)
  private val ACCOUNT_SID = MyConfig.get("twilio.sid")
  private val AUTH_TOKEN = MyConfig.get("twilio.auth")
  private val FROM = new PhoneNumber(MyConfig.get("twilio.phone"))

  Twilio.init(ACCOUNT_SID, AUTH_TOKEN)

  def send(to: String, body: String): Unit = {
    log.info("Sending sms to " + to)
    if(!MyConfig.production){
      log.info("...not really")
      return
    }
    Message.create(ACCOUNT_SID,
      new PhoneNumber(to), // To number
      FROM, // From number
      body // SMS body
    ).execute()
  }
}


