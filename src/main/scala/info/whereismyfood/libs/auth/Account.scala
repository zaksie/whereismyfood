package info.whereismyfood.libs.auth

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 11/2/16.
  */
case class Account(uuid: String, to: String, code: String)
object AccountJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val unverifiedAccountFormat = jsonFormat3(Account)
}