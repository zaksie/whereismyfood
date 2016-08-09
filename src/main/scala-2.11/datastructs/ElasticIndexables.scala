package datastructs

import com.sksamuel.elastic4s.source.Indexable
import org.json4s._
import org.json4s.native.Serialization.{write}

/**
  * Created by zakgoichman on 8/8/16.
  */

object ElasticsearchAux {
  implicit object GameIndexable extends Indexable[Game]{
    implicit val formats = native.Serialization.formats(NoTypeHints)
    override def json(x: Game): String = write(x)
  }

  implicit object WagerIndexable extends Indexable[Wager]{
    implicit val formats = native.Serialization.formats(NoTypeHints)
    override def json(x: Wager): String = write(x)
  }

  val WagerIndex = "wager"
  val WagerType = "binary"

  val GameIndex = "game"
  val GameType = "binary"
}


