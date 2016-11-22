package info.whereismyfood.libs.math

import scala.collection.mutable

/**
  * Created by zakgoichman on 11/10/16.
  */
object Misc {
  def generateNumericCode(length: Int): String = {
    ("%0"+length+"d").format(util.Random.nextInt(Math.pow(10, length).toInt))
  }

  def generateMultipleNumericCode(codeLength: Int, count: Int): Set[String] = {
    val set = mutable.Set[String]()

    while(set.size < count){
      set += generateNumericCode(codeLength)
    }

    set.toSet
  }
}
