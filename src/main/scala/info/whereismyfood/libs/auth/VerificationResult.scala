package info.whereismyfood.libs.auth

import com.google.gson.Gson
import info.whereismyfood.models.user.GenericUser

/**
  * Created by zakgoichman on 11/20/16.
  */
object VerificationResult {
  val gson = new Gson
  object Result{
    val invalid = Result(ok = false)
  }

  final case class Result(ok: Boolean, jwt: String = "")

}

final case class VerificationResult(account: Option[GenericUser]){
  import VerificationResult._

  def ok = account.isDefined
  def toJson = account match {
    case None => gson.toJson(Result.invalid)
    case Some(a) => gson.toJson(Result(ok = true, a.jwt))
  }
}