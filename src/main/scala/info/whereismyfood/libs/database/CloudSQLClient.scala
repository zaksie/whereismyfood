package info.whereismyfood.libs.database

import java.sql.{DriverManager, SQLException, Statement}

import info.whereismyfood.aux.MyConfig
import org.slf4j.LoggerFactory
import info.whereismyfood.aux.ActorSystemContainer.Implicits._
/**
  * Created by zakgoichman on 11/16/16.
  */
object CloudSQLClient {
  private val log = LoggerFactory.getLogger(this.getClass)

  private val databaseName = MyConfig.get("google.cloudsql.database-name")
  private val instanceConnectionName = MyConfig.get("google.cloudsql.instance-name")
  private val username = MyConfig.get("google.cloudsql.username")
  private val password = MyConfig.get("google.cloudsql.password")

  private val jdbcUrl = String.format(
    "jdbc:mysql://google/%s?cloudSqlInstance=%s&"
      + "socketFactory=com.google.cloud.sql.mysql.SocketFactory",
    databaseName,
    instanceConnectionName)

  val ip = MyConfig.get("google.cloudsql.ip")
  private val url = if(ip != "127.0.0.1") jdbcUrl else
    s"""jdbc:mysql://$ip/whereismyfood?user=$username
       |&password=$password""".stripMargin

  private def createConnection = {
    val c = DriverManager.getConnection(url, username, password)
    c.setNetworkTimeout(system.dispatcher, 1000*3600*24*7)
    println("CREATING NEW CONNECTION TO CLOUDSQL")
    c
  }

  private var connection = createConnection

  def createStatement(): Statement = try{
    connection.createStatement()
  }catch{
    case _: SQLException =>
      connection = createConnection
      connection.createStatement()
  }
}

trait CloudSQLStorable{
  def SaveToCloudSQL(): Boolean
}


