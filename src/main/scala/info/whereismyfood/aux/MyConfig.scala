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

  val production: Boolean = get("server.env").startsWith("prod")

  object Topics{
    type ID = String
    def courierIsOffline(implicit id: ID = "") = "courier-offline:" + id
    def courierIsOnline(implicit id: ID = "") = "courier-online:"+ id
    def courierGeolocation(implicit id: ID = "") = "courier-geolocation:"+ id
    def clientUpdates(implicit id: ID = "") = "client-updates:"+ id
    def courierUpdates(implicit id: ID = "") = "courier-updates:"+ id
    def chefUpdates(implicit id: ID = "") = "chef-updates:"+ id
  }

  object OpCodes{
    object Chef {
      val enroute = "enroute"
      val add = "add"
      val modify = "modify"
      val delete = "delete"
    }

    object Client {
      val added = "added"
      val enroute = "enroute"
      def courierPosition(courierId: String): String = "courier/position:" + courierId
    }

    object Courier {
      val pickup = "pickup"
      //TODO: currently not in use
      def clientPosition(clientId: String): String = "client/position:" + clientId
    }
  }
  object Vars {
    val recent_minutes = 5
    val vehicle_capacity = 8
    val nearby_meter = 50
  }
  object ActorNames{
    object Paths{
      val businessManager = "/user/cluster-node/" + Bare.businessManager
      val jwtToRedisJob = "/user/cluster-node/" + Bare.jwtToRedisJob
    }
    object Bare{
      val businessManager = get("actor-names.business-manager")
      val jwtToRedisJob = get("actor-names.jwt-to-redis-job")
    }
  }
}
