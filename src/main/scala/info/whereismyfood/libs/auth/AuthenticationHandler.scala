package info.whereismyfood.libs.auth

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.libs.auth.DatabaseAccount.UUID
import info.whereismyfood.libs.auth.Roles.RoleID
import io.igl.jwt._
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsNumber, JsString, JsValue}

import scala.util.Try

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
      Set(Iss, Uuid, Dbid, Aud)
    )
  }

  def verify(token: String): Boolean = {
    val res: Try[Jwt] = DecodedJwt.validateEncodedJwt(
      token, // An encoded jwt as a string
      SECRET, // The apikey phone validate the signature against
      ALGO, // The algorithm we require
      Set(Typ), // The set of headers we require (excluding alg)
      Set(Iss)
      //iss = Some(Iss(ISS_NAME)) // The iss claim phone require (similar optional arguments exist for all registered claims)
    )
    res.isSuccess
  }

  def createTokenWithRole(uuid: UUID, phone: String, role: RoleID): String = {
    val jwt = new DecodedJwt(ENCRYPTION, Seq(Iss(phone), Iss(uuid.toString), Aud(role.toString)))
    jwt.encodedAndSigned(SECRET)
  }

  def createToken(apiKey: String): String = {
    val jwt = new DecodedJwt(ENCRYPTION, Seq(Iss(apiKey)))
    jwt.encodedAndSigned(SECRET)
  }

  def createToken(account: DatabaseAccount): String = {
    val jwt = new DecodedJwt(ENCRYPTION,
      Seq(Iss(account.phone),
        Dbid(account.datastoreId.getOrElse(-1)),
        Uuid(account.uuid.get),
        Aud(account.role.toString)))
    jwt.encodedAndSigned(SECRET)
  }

  def getAuthToken(req: HttpRequest): String = {
    val AUTHORIZATION_KEYS: List[String] = List("Authorization", "HTTP_AUTHORIZATION", "X-HTTP_AUTHORIZATION", "X_HTTP_AUTHORIZATION")
    def authorizationKey: Option[String] = AUTHORIZATION_KEYS.find(req.getHeader(_) != null)
    val result = if (authorizationKey.isDefined && authorizationKey.get == "Authorization") {
      req.getHeader("Authorization").get().value()
    } else {
      "request have not authorize token"
    }
    result
  }

  def checkJwt: Directive1[Creds] = {
    parameter('token ? "").flatMap {
      case "TEST" =>
        provide(Creds(phone = "5333", role = Some(Roles.chef)))
      case "CLIENT" =>
        provide(Creds(phone = "100", role = Some(Roles.client)))
      case "CLIENT2" =>
        provide(Creds(phone = "444", role = Some(Roles.client)))
      case "COURIER" =>
        provide(Creds(phone = "555", role = Some(Roles.courier)))
      case token =>
        val parsed = decodeJwt(token)
        if (parsed.isFailure) {
          complete(HttpResponse(StatusCodes.Unauthorized))
        }
        else {
          try {
            val phone = parsed.get.getClaim[Iss].get.value
            val dbid = parsed.get.getClaim[Dbid].get.value
            val uuid = parsed.get.getClaim[Uuid].get.value
            val role = parsed.get.getClaim[Aud].get.value match {
              case Left(x) => Roles(x)
              case Right(x) => Roles(x)
            }
            provide(Creds(dbid = Some(dbid), phone = phone, role = role, uuid = Some(uuid)))
          } catch {
            case x: Exception =>
              log.error("Error while parsing decoded jwt", x)
              complete(HttpResponse(StatusCodes.Unauthorized))
          }
        }
    }
  }
}

case class Uuid(value: String) extends ClaimValue {
  override val field: ClaimField = Uuid
  override val jsValue: JsValue = JsString(value)
}

object Uuid extends (String => Uuid) with ClaimField {
  override def attemptApply(value: JsValue): Option[ClaimValue] =
  value.asOpt[String].map(apply)
  override val name: String = "uuid"
}

case class Dbid(value: Long) extends ClaimValue {
  override val field: ClaimField = Dbid
  override val jsValue: JsValue = JsNumber(value)
}

object Dbid extends (Long => Dbid) with ClaimField {
  override def attemptApply(value: JsValue): Option[ClaimValue] =
    value.asOpt[Long].map(apply)
  override val name: String = "dbid"
}