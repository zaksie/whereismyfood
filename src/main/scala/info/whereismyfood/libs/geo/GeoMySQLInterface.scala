package info.whereismyfood.libs.geo

import info.whereismyfood.aux.MyConfig
import info.whereismyfood.libs.database.Databases
import info.whereismyfood.libs.math.{Distance, DistanceParams, LatLng}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import collection.JavaConverters._
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
      val point = s"""POINT(${address.latLng.lat},${address.latLng.lng})"""
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


  def findDistanceBetween(from: LatLng, to: LatLng, withinRadius_meter: Long): Future[Option[Distance]] = {
    findNear(from, withinRadius_meter).flatMap {
      case Some(id1) =>
        findNear(to, withinRadius_meter).flatMap {
          case Some(id2) =>
            findDistanceBetween(id1, id2).map{
              case Some(params) =>
                Some(Distance(from, to, params.meters, params.seconds))
              case _ => None
            }
          case _ => Future(None)
        }
      case _ => Future(None)
    }
  }

  def findDistanceBetween(from: Long, to: Long): Future[Option[DistanceParams]] = {
    val query =
      s"""SELECT * FROM $schema.distances WHERE from_id=$from AND to_id=$to"""
    Future{
      try{
        val res = Databases.sql.createStatement().executeQuery(query)
        if(res.next()) {
          Some(DistanceParams(res.getLong("distance_in_meter"), res.getLong("distance_in_sec")))
        }
        else None
      }catch{
        case e:Exception =>
          log.error(s"Failed to execute sql query: $query [{}]", e)
          None
      }
    }
  }

  def findNear(latLng: LatLng, radius_meter: Long): Future[Option[Long]] ={
    val query =
      s"""SELECT id FROM $schema.locations WHERE ST_Distance_Sphere(location, Point(${latLng.lat},${latLng.lng})) < $radius_meter"""
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

  def saveDistances(from: LatLng, to: LatLng, distance_meter: Long, distance_sec: Long): Future[Boolean] = {
    val fromPoint = s"""POINT(${from.lat},${from.lng})"""
    val toPoint = s"""POINT(${to.lat},${to.lng})"""
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

}
