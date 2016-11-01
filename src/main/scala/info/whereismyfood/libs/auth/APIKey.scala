package info.whereismyfood.libs.auth

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by zakgoichman on 10/24/16.
  */
final case class APIKey(apikey: String)

trait APIKeyJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val apikeyFormat = jsonFormat1(APIKey)
}

