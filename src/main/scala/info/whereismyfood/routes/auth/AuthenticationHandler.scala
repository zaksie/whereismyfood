package info.whereismyfood.routes.auth

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{Credentials, FutureDirectives}
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.modules.user._
import io.igl.jwt.{Aud, _}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, JsNumber, JsString, JsValue}
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 10/23/16.
  */

case class Role(value: String) extends ClaimValue {
  override val field: ClaimField = Role
  override val jsValue: JsValue = JsString(value)
}

object Role extends ClaimField {
  override def attemptApply(value: JsValue): Option[ClaimValue] = value.asOpt[String].map(apply)

  override val name = "role"
}



trait AuthenticationHandler {
  val log = LoggerFactory.getLogger("AuthenticationHandler")

  val GUEST = "guest"

  val ISS_NAME = "whereismyfood"
  val ALGO = Algorithm.HS512
  val ENCRYPTION = Seq(Alg(ALGO), Typ("JWT"))
  val SECRET = MyConfig.get("jwt.secret")

  def decodeJwt(token: String): Try[Jwt] = {
    DecodedJwt.validateEncodedJwt(
      token, // An encoded jwt as a string
      SECRET, // The apikey phone validate the signature against
      ALGO, // The algorithm we require
      Set(Typ), // The set of headers we require (excluding alg)
      Set(Iss, Uuid, Aud, Busid)
    )
  }

  def createTokenFromUser(account: GenericUser, expiry: Option[Duration] = None): String = {
    createTokenFromCreds(account.creds, expiry)
  }

  def createTokenFromCreds(creds: Creds, expiry: Option[Duration] = None): String = {
    createToken(creds.phone,
      creds.deviceId.get,
      creds.businessIds,
      creds.role, expiry)
  }

  def createToken(userId: String, uuid: String, businessIds: Set[Long], role: Long, expiry: Option[Duration] = None): String = {
    def getExp = {
      //throws exception if expiry not defined
      Exp(System.currentTimeMillis + expiry.get.toMillis)
    }

    val claims = Seq(Iss(userId),
      Uuid(uuid),
      Busid(businessIds),
      Aud(role.toString))

    val jwt = new DecodedJwt(ENCRYPTION,
      if(expiry.isDefined) claims :+ getExp else claims)

    jwt.encodedAndSigned(SECRET)
  }

  def checkCredentials(credentials: Credentials): Future[Option[Creds]] = {
    credentials match {
      case p@Credentials.Provided(token) =>
        checkJwtToken(token) match {
          case (creds@Some(_), 200, _) =>
            Future.successful(creds)
          case _ =>
            println("Shit")
            Future.successful(None)
        }
      case _ =>
        println("Shitter")
        Future.successful(None)
    }
  }

  protected def checkJwtToken(token: String): (Option[Creds], Int, String) ={
    if(token.startsWith(GUEST)){
      return (Some((ClientUser getOrCreate token).toCreds()), 200, "")
    }

    val parsed = decodeJwt(token)
    if (parsed.isFailure) {
      (None, 401, "Error parsing token")
    }
    else {
      try {
        parsed.get.getClaim[Exp] match {
          case Some(exp) if exp.value < System.currentTimeMillis =>
            return upgradeRequired()
          case _ =>
        }

        val phone = parsed.get.getClaim[Iss].get.value
        UserRouter.getJwtFor(phone) match {
          case Some(jwt) if jwt != token =>
              return upgradeRequired(jwt)
          case _ =>
        }

        val uuid = parsed.get.getClaim[Uuid].get.value
        val busid = parsed.get.getClaim[Busid].get.value
        val role = parsed.get.getClaim[Aud].get.value match {
          case Left(x) => Roles(x)
          case Right(x) => Roles(x)
        }
        val creds = Creds(phone = phone, uuid = Some(uuid))
        //This is separate from creds because I don't want to receive it from the user
        creds.setRole(role)
        creds.setBusinesses(busid)
        (Some(creds), 200, "")
      } catch {
        case x: Exception =>
          log.error("Error while parsing decoded jwt", x)
          (None, 401, "Invalid token")
      }
    }
  }
  protected def upgradeRequired(res: String = ""): (Option[Creds], Int, String) = (None, StatusCodes.UpgradeRequired.intValue, res)
  private val jwtRegex = """"jwt":\s*"(.*?)"""".r

  private def extractJwt(data: String): String ={
    jwtRegex findFirstIn data match{
      case Some(jwtRegex(jwt)) => jwt
      case _ => ""
    }
  }
}

final case class JWTEntity(jwt: String)
object JWTEntityJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val format = jsonFormat1(JWTEntity)
}

case class Uuid(value: String) extends ClaimValue {
  override val field: ClaimField = Uuid
  override val jsValue: JsValue = JsString(value)
}

object Uuid extends (String => Uuid) with ClaimField {
  override def attemptApply(value: JsValue): Option[ClaimValue] =
  value.asOpt[String].map(apply)
  override val name: String = "deviceId"
}

case class Busid(value: Set[Long]) extends ClaimValue {
  override val field: ClaimField = Busid
  override val jsValue: JsValue = JsArray(value.map(x => JsNumber(BigDecimal(x))).toSeq)
}

object Busid extends (Set[Long] => Busid) with ClaimField {
  override def attemptApply(value: JsValue): Option[ClaimValue] =
    value.asOpt[Set[Long]].map(apply)
  override val name: String = "businessIds"
}
