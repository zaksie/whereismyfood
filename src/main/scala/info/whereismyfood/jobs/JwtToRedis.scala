package info.whereismyfood.jobs

import akka.actor.{Actor, ActorContext, ActorLogging, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.cloud.datastore.{Entity, Query, ReadOption}
import info.whereismyfood.aux.MyConfig.ActorNames
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.modules.business.Business
import info.whereismyfood.modules.user.GenericUser
import info.whereismyfood.routes.auth.AuthenticationHandler

import scala.collection.JavaConverters._
import concurrent.duration._
/**
  * Created by zakgoichman on 1/13/17.
  */
object JwtToRedis {
  case object Go
  def start(implicit context: ActorContext, cluster: Cluster) ={
    //Scans for changes in business records in db
    val ref1 = context.actorOf(ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(context.system)),
      ActorNames.Bare.jwtToRedisJob)

    println(s"JwtToRedis path: ${ref1.path}")

    val job = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = ActorNames.Paths.jwtToRedisJob,
        settings = ClusterSingletonProxySettings(context.system)),
      name = ActorNames.Bare.jwtToRedisJob + "-proxy")

    import scala.concurrent.duration._
    context.system.scheduler.schedule(
      0 milliseconds,
      1 hour,
      job,
      Go)(context.system.dispatcher)
  }

  val props = Props[JwtToRedisActor]

  val SAVE_DURATION = 3 hours
}

class JwtToRedisActor extends Actor with ActorLogging {
  import JwtToRedis._
  private val datastore = info.whereismyfood.libs.database.Databases.persistent.client
  private var offset = 0
  private val limit = 2000
  override def receive = {
    case Go =>
      createJwtsOnRedis()
  }

  def createJwtsOnRedis() = {
    def func(entity: Entity) = {
      val f0: String => Unit = saveJwt(entity, _: String)
      val f1: Set[Long] => String = createJwt(entity, _: Set[Long])
      val f = f0 compose f1 compose getBusinesses _ compose getUserId _
      f(entity)
    }
    getUsers.foreach(func)
  }

  def getUserId(entity: Entity): String = {
    entity.getString("phone")
  }

  def getBusinesses(userId: String): Set[Long] = {
    Business.getIdsFor(userId, Business.DSTypes.apiers) union
    Business.getIdsFor(userId, Business.DSTypes.couriers) union
    Business.getIdsFor(userId, Business.DSTypes.chefs) union
    Business.getIdsFor(userId, Business.DSTypes.owners)
  }

  def createJwt(entity: Entity, ids: Set[Long]): String = {
      Jwt(entity.getString("phone"),
        entity.getString("deviceId"),
        ids,
        entity.getLong("role")
      ).create
  }

  def saveJwt(entity: Entity, jwt: String): Unit = {
    Databases.inmemory.save[String](SAVE_DURATION, (s"jwt/${entity.getString("phone")}", jwt))
  }

  def getUsers: Seq[Entity] = {
    try {
      val q: Query[Entity] = Query.newEntityQueryBuilder()
          .setKind(GenericUser.USER_KIND)
              .setLimit(limit)
              .setOffset(offset * limit)
          .build

      val res = datastore.run(q, ReadOption.eventualConsistency()).asScala.toSeq
      if(res.length < limit) offset = 0

      res
    }catch{
      case e: Throwable =>
        log.error("Error in JOB JwtToRedis", e)
        Seq()
    }
  }

  case class Jwt(phone: String, uuid: String, businesses: Set[Long], role: Long) extends AuthenticationHandler {
    def create: String = createToken(phone, uuid, businesses, role)
  }
}
