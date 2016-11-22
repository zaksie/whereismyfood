package info.whereismyfood.libs.auth

import info.whereismyfood.libs.database.{Databases, KVStorable}
import info.whereismyfood.models.user.Creds

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by zakgoichman on 11/10/16.
  */
object OTP {
  def retrieve(key: String): Option[String] = {
    Try {
      Await.result[Option[String]](Databases.inmemory.retrieve(key), 20 seconds).get
    }.toOption
  }

  def save(key: String, otp: String): Unit = {
    Await.result(Databases.inmemory.save[String](1 hour, (key, otp)), 20 seconds)
  }
}
