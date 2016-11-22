package info.whereismyfood.libs.geo

import info.whereismyfood.libs.math.LatLng

import scala.util.matching.Regex

/**
  * Created by zakgoichman on 11/17/16.
  */

case class DistanceMatrixRequestParams(start: LatLng, destinations: Set[LatLng]){
  val coordInputPattern = new Regex("""\((.*?)\)""")
  def getLocations: Seq[LatLng] = {
    start +: destinations.toSeq //TODO: The conversion to Set is like running distinct?
  }
}

