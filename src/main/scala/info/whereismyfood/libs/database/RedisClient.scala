package info.whereismyfood.libs.database

import akka.actor.Status.{Failure, Success}
import redis.protocol.MultiBulk
import redis.{RedisClient => RedisClientLib}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
/**
  * Created by zakgoichman on 10/30/16.
  */
class RedisClient private extends DatabaseImplementation {
  val redis = RedisClientLib()

  val futurePong = redis.ping()
  println("Ping sent!")
  futurePong.map(pong => {
    println(s"Redis replied with a $pong")
  })
  Await.result(futurePong, 5 seconds)

  def save[T](items: Seq[KeyValueStorable[T]], withTransaction: Boolean): Future[MultiBulk] = {
    val keys = items.map(_.key)
    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch(keys:_*) // watch for changes to key
    items.foreach { i =>
      redisTransaction.set(i.key, i.value)
    }
    redisTransaction.exec()
  }

  def retrieve[T](keys: String*): Seq[T] = {
    redis.mget(keys).map[T]{
      v => 
    }
  }
}


object RedisClient{
  val instance = new RedisClient
}