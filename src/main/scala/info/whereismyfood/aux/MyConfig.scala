package info.whereismyfood

import com.typesafe.config.ConfigFactory

/**
  * Created by zakgoichman on 10/21/16.
  */
object MyConfig {
  val inst = ConfigFactory.load()
}
