package info.whereismyfood.libs.math

import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Entity, LatLngValue, Query, QueryResults, ReadOption, LatLng => DSLatLng}
import info.whereismyfood.libs.database.{DatastoreFetchable, DatastoreStorable}

import collection.JavaConverters._

/**
  * Created by zakgoichman on 10/25/16.
  */


object DistanceMatrix extends DatastoreFetchable[DistanceMatrix] {
  def getFromDB(param: Any): Option[DistanceMatrix] = getFromDatastore(param)
  override def getFromDatastore(params: Any): Option[DistanceMatrix] = {
    val locations = params.asInstanceOf[Seq[LatLng]]
    val distanceMatrix = DistanceMatrix()

    for(l <- locations) {
      val fromQueries = get("from", l)
      val toQueries = get("to", l)

      fromQueries.asScala.foreach(r => distanceMatrix.add(new Distance(r)))
      toQueries.asScala.foreach(r => distanceMatrix.add(new Distance(r)))
    }

    def get(fieldName: String, location: LatLng): QueryResults[Entity] = {
      val q = Query.newEntityQueryBuilder
        .setKind(Distance.kind)
        .setFilter(PropertyFilter.eq(fieldName, new LatLngValue(DSLatLng.of(location.lat, location.lng))))
        .build

      datastore.run(q, ReadOption.eventualConsistency())
    }

    Some(distanceMatrix)
  }
}
case class DistanceMatrix() {
  private val distances = new scala.collection.mutable.ArrayBuffer[Distance]

  def this(ds: Seq[Distance]) = {
    this
    add(ds:_*)
  }

  def add(ds: Distance*): DistanceMatrix = {
    for(d <- ds) {
      val hash = d.hashCode
      if (!distances.exists(_.hashCode == hash)) {
        distances.append(d)
      }
    }
    this
  }

  def getAll : Seq[Distance] = distances
  def get(from: LatLng, to: LatLng): Option[Distance] = distances.find(d => d.from.latLng == from && d.to.latLng == to)
  def size = {
    distances.map(_.from).distinct.length
  }

  def merge(dm2: DistanceMatrix): DistanceMatrix = {
    this.add(dm2.distances: _*)
    this
  }
}

