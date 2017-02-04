package info.whereismyfood.modules.payment

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.whereismyfood.modules.user.GenericUser
import org.apache.http.Consts
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 1/24/17.
  */

object CreditCard{
  val LAST_DIGITS_MOD = 4
  val log = LoggerFactory.getLogger(this.getClass)
}
case class ExpiryDate(month: Int, year: Int)
case class CardHolder(name: String, id: String)
case class CreditCard(number: Long, expiryDate: ExpiryDate, cvv: Int, cardHolder: CardHolder) {

  import CreditCard._

  def lastDigits: Int = number % Math.pow(10, LAST_DIGITS_MOD) toInt

  def validate: Boolean = true // TODO: stub

  def toPaymentMethod(user: GenericUser): Option[PaymentMethod] = {
    try {
      Option(PaymentMethod(None, user.ref, PaymentMethod.CREDIT_CARD,
        None, None, None, cardHolder.name, lastDigits.toString))
    } catch {
      case e: Throwable =>
        log.error("Failed in PaymentMethod.of", e)
        None
    }
  }
}


object CreditCardJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val expiryDateformatter = jsonFormat2(ExpiryDate)
  implicit val cardHolderformatter = jsonFormat2(CardHolder)
  implicit val creditCardformatter = jsonFormat4(CreditCard.apply)
}