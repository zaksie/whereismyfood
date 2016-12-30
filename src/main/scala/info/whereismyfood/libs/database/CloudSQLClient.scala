package info.whereismyfood.libs.database

import java.sql.{DriverManager, SQLException, Statement}

import info.whereismyfood.aux.MyConfig
import org.slf4j.LoggerFactory

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
    instanceConnectionName);

  private val url =
    s"""jdbc:mysql://104.199.69.216/whereismyfood?user=$username
       |&password=$password""".stripMargin
  private def createConnection = DriverManager.getConnection(url, username, password)
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


