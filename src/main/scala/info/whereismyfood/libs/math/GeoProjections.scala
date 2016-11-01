package info.whereismyfood.libs.math

import com.google.maps.model.LatLng
/**
  * Created by zakgoichman on 10/25/16.
  */
object GeoProjections {
  val R = 6371000 // Earth radius in meters
  def MercatorProjection(sphericalLocation: LatLng): (Int, Int) = {
    if(sphericalLocation == null) return (0,0)

    val lat = sphericalLocation.lat
    val lng = sphericalLocation.lng

    ((R * lng).toInt,
      (R * Math.log( Math.tan((lat+Math.PI/2)/2) )).toInt)
  }
}
