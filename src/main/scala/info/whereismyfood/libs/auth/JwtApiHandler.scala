package info.whereismyfood.libs.auth

/**
  * Created by zakgoichman on 10/23/16.
  */

trait JwtApiHandler extends APIKeyJsonSupport {
  def getLoginInfo(key: String): Option[LoginEntity] = {
    LoginEntity.getFromDatastore(key)
  }
}
