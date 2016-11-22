package info.whereismyfood.routes.auth

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, RequestEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.FutureDirectives
import akka.http.scaladsl.server.directives.FutureDirectives.{onComplete => _, _}
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
import info.whereismyfood.models.user._
import io.igl.jwt.{Aud, _}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, JsNumber, JsString, JsValue}
import spray.json.DefaultJsonProtocol

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

  val ISS_NAME = "whereismyfood"
  val ALGO = Algorithm.HS512
  val ENCRYPTION = Seq(Alg(ALGO), Typ("JWT"))
  val SECRET = MyConfig.get("jwt.secret")

  val SHORT_EXP = Exp(System.currentTimeMillis()/1000 + (3 hours).toSeconds)
  def decodeJwt(token: String): Try[Jwt] = {
    DecodedJwt.validateEncodedJwt(
      token, // An encoded jwt as a string
      SECRET, // The apikey phone validate the signature against
      ALGO, // The algorithm we require
      Set(Typ), // The set of headers we require (excluding alg)
      Set(Iss, Uuid, Aud, Busid)
    )
  }

  def createToken(account: GenericUser): String = {
    val jwt = new DecodedJwt(ENCRYPTION,
      Seq(Iss(account.phone),
        Uuid(account.deviceId.get),
        Busid(account.businessIds),
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
      case "" =>
        extract { ctx =>
          import ctx.materializer
          ctx.request.entity.toStrict(FiniteDuration(5, TimeUnit.MILLISECONDS))
        }.flatMap { entity =>
          import FutureDirectives._

          onComplete(entity).flatMap {
            case Success(x) ⇒
              val body = x.getData().utf8String
              val token = extractJwt(body)
              checkJwtToken(token)
            case Failure(t) ⇒ complete(401)
          }
        }
      case token =>
        checkJwtToken(token)
    }
  }

  private def checkJwtToken(token: String): Directive1[Creds] ={
    val parsed = decodeJwt(token)
    if (parsed.isFailure) {
      complete(401)
    }
    else {
      try {
        val phone = parsed.get.getClaim[Iss].get.value
        val uuid = parsed.get.getClaim[Uuid].get.value
        val busid = parsed.get.getClaim[Busid].get.value
        val role = parsed.get.getClaim[Aud].get.value match {
          case Left(x) => Roles(x)
          case Right(x) => Roles(x)
        }
        val creds = Creds(phone = phone, deviceId = Some(uuid))
        //This is separate from creds because I don't want to receive it from the user
        creds.setRole(role)
        creds.setBusinesses(busid)
        provide(creds)
      } catch {
        case x: Exception =>
          log.error("Error while parsing decoded jwt", x)
          complete(401)
      }
    }
  }
  val jwtRegex = """"jwt":\s*"(.*?)"""".r

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
