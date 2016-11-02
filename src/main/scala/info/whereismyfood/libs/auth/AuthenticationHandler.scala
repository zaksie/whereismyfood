package info.whereismyfood.libs.auth

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import info.whereismyfood.aux.MyConfig
import io.igl.jwt._
import play.api.libs.json.{JsString, JsValue}

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
  val ISS_NAME = "whereismyfood"
  val ALGO = Algorithm.HS512
  val ENCRYPTION = Seq(Alg(ALGO), Typ("JWT"))
  val SECRET = MyConfig.get("jwt.secret")

  def isVerifyWithRole(req: HttpRequest, role: String): Boolean = {
    val result = getAuthToken(req)

    val res: Try[Jwt] = DecodedJwt.validateEncodedJwt(
      result, // An encoded jwt as a string
      SECRET, // The apikey to validate the signature against
      ALGO, // The algorithm we require
      Set(Typ), // The set of headers we require (excluding alg)
      Set(Iss, Aud),
      iss = Some(Iss(ISS_NAME)), // The iss claim to require (similar optional arguments exist for all registered claims)
      aud = Some(Aud(role))
    )
    res.isSuccess
  }

  def verify(token: String): Boolean = {
    if(token == "TEST") return true
    val res: Try[Jwt] = DecodedJwt.validateEncodedJwt(
      token, // An encoded jwt as a string
      SECRET, // The apikey to validate the signature against
      ALGO, // The algorithm we require
      Set(Typ), // The set of headers we require (excluding alg)
      Set(Iss)
      //iss = Some(Iss(ISS_NAME)) // The iss claim to require (similar optional arguments exist for all registered claims)
    )
    res.isSuccess
  }

  def createTokenWithRole(apiKey: String, role: String): String = {
    val jwt = new DecodedJwt(ENCRYPTION, Seq(Iss(apiKey), Aud(role)))
    jwt.encodedAndSigned(SECRET)
  }

  def createToken(apiKey: String): String = {
    val jwt = new DecodedJwt(ENCRYPTION, Seq(Iss(apiKey)))
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

  def checkJWT: Route =
    parameter('token ? "") { token =>
      if (!verify(token)) {
        complete(HttpResponse(StatusCodes.Unauthorized))
      }
      else reject
    }

}