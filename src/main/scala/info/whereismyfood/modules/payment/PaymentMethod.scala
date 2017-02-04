package info.whereismyfood.modules.payment

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.cloud.datastore._
import info.whereismyfood.libs.database.{Databases, DatastoreFetchable, DatastoreStorable}
import info.whereismyfood.modules.payment.PaymentMethod.PaymentType
import info.whereismyfood.modules.user.{Creds, GenericUser}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.util.Try
import collection.JavaConverters._
/**
  * Created by zakgoichman on 1/20/17.
  */
object PaymentMethod extends DatastoreFetchable[PaymentMethod]{
  val log = LoggerFactory.getLogger(this.getClass)

  type PaymentType = String
  val CREDIT_CARD: PaymentType = "creditcard"
  val corporate: PaymentType = "corporate"
  val kind = "PaymentMethod"
  object Fields{
    val id = "id"
    val `type` = "type"
    val restrictedText = "restrictedText"
    val userRef = "userRef"
    val fullName = "fullName"
    val corporateName = "corporateName"
    val corporateImage = "corporateImage"
    val lastDigits = "lastDigits"
    val leftDaily = "leftDaily"
    val leftMonthly = "leftMonthly"
  }

  def find(ref: String): Seq[PaymentMethod] = {
    try {
      val gql = s"SELECT * FROM $kind WHERE userRef = @ref"
      val t = Query.ResultType.ENTITY
      val q = Query.newGqlQueryBuilder(t, gql)
          .setBinding("ref", ref)
          .build
      val records = datastore.run(q, ReadOption.eventualConsistency()).asScala
      records.flatMap(of).toSeq
    } catch {
      case _: Throwable =>
        log.error("Failed to get payment methods for ref " + ref)
        Seq()
    }
  }
  def of(entity: Entity): Option[PaymentMethod] = {
    Try {
      import Fields._
      PaymentMethod(
        Some(entity.getKey.getId),
        entity.getString(userRef),
        entity.getString(`type`),
        Try(entity.getString(restrictedText)).toOption,
        Try(entity.getString(corporateName)).toOption,
        Try(entity.getString(corporateImage)).toOption,
        entity.getString(fullName),
        entity.getString(lastDigits),
        Try(entity.getString(leftDaily)).toOption,
        Try(entity.getString(leftMonthly)).toOption
      )
    }.toOption
  }
}

case class PaymentMethod(id: Option[Long] = None, userRef: String, `type`: PaymentType, restrictedText: Option[String] = None /*in case corporation decided to restrict to hours, businesses*/,
                         corporateName: Option[String] = None, corporateImage: Option[String] = None, fullName: String,
                         lastDigits: String, leftDaily: Option[String] = None, leftMonthly: Option[String] = None) extends DatastoreStorable{
  import PaymentMethod._
  override def asDatastoreEntity: Option[FullEntity[_ <: IncompleteKey]] = {
    Try {
      val key = datastore.newKeyFactory().setKind(kind)
      FullEntity.newBuilder(if(id.isDefined) key.newKey(id.get) else key.newKey())
          .set(Fields.userRef, userRef)
          .set(Fields.`type`, `type`)
          .set(Fields.restrictedText, restrictedText.getOrElse(""))
          .set(Fields.corporateName, corporateName.getOrElse(""))
          .set(Fields.corporateImage, corporateImage.getOrElse(""))
          .set(Fields.fullName, fullName)
          .set(Fields.lastDigits, lastDigits)
          .set(Fields.leftDaily, leftDaily.getOrElse(""))
          .set(Fields.leftMonthly, leftMonthly.getOrElse(""))
          .build
    }.toOption
  }
}


object PaymentMethodJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val paymentMethodformatter = jsonFormat10(PaymentMethod.apply)
}