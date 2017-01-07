package info.whereismyfood.modules.geo

import com.google.maps.model.EncodedPolyline
import com.mysql.jdbc.CommunicationsException
import info.whereismyfood.aux.MyConfig
import info.whereismyfood.aux.MyConfig.Vars
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.modules.business.BusinessPublic
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConverters._
/**
  * Created by zakgoichman on 11/17/16.
  */
object GeoMySQLInterface {
  val log = LoggerFactory.getLogger(this.getClass)
  val schema = MyConfig.get("mysql.schema")
  
  case class EscapedString(str: String){
    def escape:String = "'" + str.replace("'", "\\'") + "'"
  }
  implicit def stringToEscapedString(s: String) = EscapedString(s)
  def saveAddressAndLocation(addresses: Address*): Future[Boolean] = {
    def createQueries(address: Address): Seq[String] = {
      val point = s"""POINT(${address.latLng.lng},${address.latLng.lat})"""
      val q1 = s"""INSERT IGNORE INTO $schema.locations (location) VALUES ($point)"""
      val q2 = s"""INSERT IGNORE INTO $schema.addresses (id, address)
                   | SELECT id, ${address.raw.escape} FROM $schema.locations WHERE location=$point""".stripMargin
      Seq(q1, q2)
    }

    Future{
      try{
        val statement = Databases.sql.createStatement()
        addresses.foreach { a =>
          createQueries(a).foreach { q =>
            statement.addBatch(q)
          }
        }
        statement.executeBatch()
        true
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: {}", e)
          false
      }
    }
  }

  def findByAddress(address: String): Future[Option[LatLng]] = {
    val query =
      s"""SELECT ST_X(location), ST_Y(location) FROM $schema.locations
        |WHERE id=(SELECT id FROM $schema.addresses WHERE address = ${address.escape})""".stripMargin
    Future{
      try{
        val res = Databases.sql.createStatement().executeQuery(query)
        if(res.next()) {
          Some(LatLng(res.getDouble(1), res.getDouble(2)))
        }
        else None
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          None
      }
    }
  }
  def findIds(points: Seq[LatLng])(implicit withinRadius_meter: Long = Vars.nearby_meter): Future[Seq[Option[Long]]] = {
    Future.sequence(points.map(findNear))
  }
  def findDistanceBetween(from: LatLng, to: LatLng)(implicit withinRadius_meter: Long = Vars.nearby_meter): Future[Option[Distance]] = {
    findIds(Seq(from, to)).flatMap {
      case Seq(a, b) if a.isDefined && b.isDefined =>
        findDistanceBetween(a.get, b.get).map {
          case Some(params) =>
            Some(Distance(from, to, params.meters, params.seconds))
          case _ => None
        }
      case _ => Future.successful(None)
    }
  }

  private def findDistanceBetween(from: Long, to: Long): Future[Option[DistanceParams]] = {
    val query =
      s"""SELECT * FROM $schema.distances WHERE from_id=$from AND to_id=$to"""
    Future{
      try{
        val res = Databases.sql.createStatement().executeQuery(query)
        if(res.next()) {
          Some(DistanceParams(res.getLong("distance_meter"), res.getLong("distance_sec")))
        }
        else None
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          None
      }
    }
  }

  def findRoute(from: LatLng, to: LatLng)(implicit withinRadius_meter: Long = Vars.nearby_meter): Future[Option[String]] = {
    findIds(Seq(from, to)).flatMap {
      case Seq(a, b) if a.isDefined && b.isDefined =>
        findRoute(a.get, b.get).map {
          case s@Some(_) if s.get != null  => s
          case _ => None
        }
      case _ => Future.successful(None)
    }
  }

  private def findRoute(from: Long, to: Long): Future[Option[String]] = {
    val query =
      s"""SELECT route FROM $schema.distances WHERE from_id=$from AND to_id=$to"""
    Future{
      try{
        val res = Databases.sql.createStatement().executeQuery(query)
        if(res.next()) {
          Some(res.getString("route"))
        }
        else None
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          None
      }
    }
  }

  def findNear(latLng: LatLng)(implicit radius_meter: Long = Vars.nearby_meter): Future[Option[Long]] ={
    val query =
      s"""SELECT id FROM $schema.locations WHERE ST_Distance_Sphere(location, Point(${latLng.lng},${latLng.lat})) < $radius_meter
         |ORDER BY St_distance_sphere(location, Point(${latLng.lng},${latLng.lat})) ASC
       """.stripMargin
    Future{
      try{
        val res = Databases.sql.createStatement().executeQuery(query)
        if(res.next()) {
          Some(res.getLong("id"))
        }
        else None
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          None
      }
    }
  }

  def findBusinessesNearMe(me: LatLng)(implicit radius_meter: Long = Vars.nearby_meter): Future[Set[BusinessPublic]] = {
    val query =
      s"""SELECT *, ST_X(location), ST_Y(location) FROM $schema.businesses WHERE ST_Distance_Sphere(location, Point(${me.lng},${me.lat})) < $radius_meter
         |ORDER BY St_distance_sphere(location, Point(${me.lng},${me.lat})) ASC
       """.stripMargin
    Future{
      try{
        val res = Databases.sql.createStatement().executeQuery(query)
        val resultSet = collection.mutable.Set[BusinessPublic]()
        while(res.next()) {
          resultSet += BusinessPublic(
            res.getLong("id"),
            res.getString("name"),
            res.getString("image"),
            res.getDouble("rating"),
            res.getInt("raters"),
            res.getString("main_menu"),
            LatLng(res.getDouble(8), res.getDouble(9))
          )
        }
        resultSet.toSet
      }catch{
        case e: CommunicationsException =>
          Set()
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          Set()
      }
    }
  }

  def saveDistances(from: LatLng, to: LatLng, distance_meter: Long, distance_sec: Long): Future[Boolean] = {
    val fromPoint = s"""POINT(${from.lng},${from.lat})"""
    val toPoint = s"""POINT(${to.lng},${to.lat})"""
    val query =
      s"""INSERT IGNORE INTO $schema.distances (from_id, to_id, distance_meter, distance_sec)
          | SELECT id, (SELECT id FROM $schema.locations WHERE location = $toPoint),
          |  $distance_meter, $distance_sec FROM $schema.locations WHERE location = $fromPoint""".stripMargin

    Future{
      try{
        Databases.sql.createStatement().execute(query)
        true
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          false
      }
    }
  }
  def savePolyline(from: LatLng, to: LatLng, x: EncodedPolyline) = {
    val fromPoint = s"""POINT(${from.lng},${from.lat})"""
    val toPoint = s"""POINT(${to.lng},${to.lat})"""
    val query =
      s"""UPDATE $schema.distances SET route = ${x.getEncodedPath.escape}
          | WHERE from_id=(SELECT id FROM $schema.locations WHERE location = $fromPoint)
          | AND to_id=(SELECT id FROM $schema.locations WHERE location = $toPoint)""".stripMargin

    Future{
      try{
        Databases.sql.createStatement().execute(query)
        true
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          false
      }
    }
  }
}
