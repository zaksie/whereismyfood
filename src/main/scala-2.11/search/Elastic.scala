package search

import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import com.sksamuel.elastic4s.ElasticDsl._
import datastructs._
import datastructs.ElasticsearchAux._
import akka.actor.{Actor}

/**
  * Created by zakgoichman on 8/8/16.
  */
object ElasticModule {
  val client = ElasticClient.local

  def insert(game: Game) = {
    client.execute {
      index into GameIndex / GameType id game.event.id source game
    }
  }

  def retrieve(gameId: String) = {
    client.execute {
      ElasticDsl.get id gameId from GameIndex / GameType
    }
  }

  def searchFor(q: String) = {
    client.execute {
      search in GameIndex / GameType query q
    }
  }
}

class ElasticRequestActor extends Actor {
  def handleRequest(query: String) = {
    ElasticModule.searchFor(query)
  }

  override def receive = {
    case query : String =>
      sender ! handleRequest(query)
  }
}
