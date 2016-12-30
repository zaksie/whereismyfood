package info.whereismyfood.routes.api.v1.http.templates

import java.io.FileNotFoundException

/**
  * Created by zakgoichman on 12/26/16.
  */
object Common {
  def loadTemplate(p: String): String = {
    Option(getClass.getClassLoader.getResourceAsStream(p)).map(scala.io.Source.fromInputStream)
        .map(_.getLines.mkString("\n"))
        .getOrElse(throw new FileNotFoundException(p))
  }
}
