package info.whereismyfood.aux

import com.typesafe.config.ConfigFactory

/**
  * Created by zakgoichman on 10/21/16.
  */
object MyConfig {
  private val inst = ConfigFactory.load()
  def config = inst
  def get(key: String) = {
    inst.getString(key)
  }

  def getInt(key: String) = {
    inst.getInt(key)
  }

  object Topics{
    val courierGeolocation = "courier-geolocation"
    val clientUpdates = "client-updates"
    val courierUpdates = "courier-updates"
  }

  object ActorNames{
    object Paths{
      val businessManager = "/user/cluster-node/" + get("actor-names.business-manager")
    }
    object Bare{
      val businessManager = get("actor-names.business-manager")
    }
  }
}
