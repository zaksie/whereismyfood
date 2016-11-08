package info.whereismyfood.libs.database

import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import redis.protocol.MultiBulk
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient => RedisClientLib}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 10/30/16.
  */
class RedisClient private {
  def addToList[T<:KVStorable](key: String, items: Seq[T], expiry: Duration = 30 days)(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(key) // watch for changes to key
    redisTransaction.lpush(key, items:_*)
    redisTransaction.expire(key, expiry.toSeconds)
    redisTransaction.exec()
  }

  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer
  implicit val executionContext = system.dispatcher

  private val redis = RedisClientLib(MyConfig.get("redis.host"), MyConfig.getInt("redis.port"), Some(MyConfig.get("redis.pwd")))

  def saveSeq[T <: KVStorable](items: Seq[T])(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    save[T](items.map(t=> (t.key, t)):_*)
  }

  def save[T](items: (String, T)*)(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(items.map(_._1):_*) // watch for changes to key
    items.foreach { i =>
      redisTransaction.set[T](i._1, i._2, Some(60*60*3L))
    }
    redisTransaction.exec()
  }

  def retrieve[T <: KVStorable](keys: String*)(implicit bsd: ByteStringDeserializer[T]) : Future[Seq[T]] = {
      redis.mget[T](keys:_*).flatMap { x =>
        Future(x.flatten)
      }
  }
}

object RedisClient{
  val instance = new RedisClient
}