package info.whereismyfood.libs.geo

import com.google.maps.GeoApiContext
import info.whereismyfood.aux.MyConfig

/**
  * Created by zakgoichman on 11/25/16.
  */
object GoogleGeoAPIContext {
  val geoApiContext = new GeoApiContext().setApiKey(MyConfig.get("google.apikey"))
}
