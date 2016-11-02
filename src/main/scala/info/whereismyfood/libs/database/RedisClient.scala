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
  implicit val system = ActorSystemContainer.getSystem
  implicit val materializer = ActorSystemContainer.getMaterializer
  implicit val executionContext = system.dispatcher

  private val redis = RedisClientLib(MyConfig.get("redis.host"), MyConfig.getInt("redis.port"), Some(MyConfig.get("redis.pwd")))

  def save[T <: KVStorable](items: Seq[T])(implicit bsd: ByteStringSerializer[T]): Future[MultiBulk] = {
    val keys = items.map(_.key)
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(keys:_*) // watch for changes to key
    items.foreach { i =>
      redisTransaction.set[T](i.key, i, Some((30 days) toSeconds))
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