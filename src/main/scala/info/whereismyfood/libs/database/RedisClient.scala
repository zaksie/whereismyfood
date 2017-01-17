package info.whereismyfood.libs.database

import info.whereismyfood.aux.{ActorSystemContainer, MyConfig}
import redis.protocol.MultiBulk
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient => RedisClientLib}

import scala.concurrent.Future
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 10/30/16.
  */
class RedisClient private {
  import ActorSystemContainer.Implicits._
  import concurrent.ExecutionContext.Implicits.global

  val redis = RedisClientLib(MyConfig.get("redis.host"), MyConfig.getInt("redis.port"), Some(MyConfig.get("redis.pwd")))

  def addToSet[T](expiry: Duration, key: String, items: T*)(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(key) // watch for changes to key
    redisTransaction.sadd(key, items: _*)
    redisTransaction.expire(key, expiry.toSeconds)
    redisTransaction.exec()
  }

  def delFromSet[T](key:String, items: T*)(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(key) // watch for changes to key
    redisTransaction.srem(key, items: _*)
    redisTransaction.exec()
  }

  def retrieveSet[T](key: String)(implicit bsd: ByteStringDeserializer[T]): Future[Seq[T]] = {
      redis.smembers[T](key)
  }


  def saveSeq[T <: KVStorable](expiry: Duration, items: Seq[T])(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    save(expiry, items.map(x=>(x.key, x)):_*)
  }

  def save[T](expiry: Duration, items: (String, T)*)(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(items.map(_._1): _*) // watch for changes to key
    items.foreach { i =>
      redisTransaction.set[T](i._1, i._2, Some(expiry.toSeconds))
    }
    redisTransaction.exec()
  }

  def retrieve[T](keys: String*)(implicit bsd: ByteStringDeserializer[T]): Future[Seq[T]] = {
    redis.mget[T](keys: _*).map(_.flatten)
  }

  def retrieve(key: String): Future[Option[String]] = {
    redis.get[String](key)
  }

  def delete(key: String): Future[Long] = {
    redis.del(key)
  }
}

object RedisClient{
  val instance = new RedisClient
}