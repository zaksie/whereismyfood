package info.whereismyfood.libs.database

import java.sql.DriverManager

import info.whereismyfood.aux.MyConfig
import org.slf4j.LoggerFactory

/**
  * Created by zakgoichman on 11/16/16.
  */
object CloudSQLClient {
  private val log = LoggerFactory.getLogger(this.getClass)

  val databaseName = MyConfig.get("google.cloudsql.database-name")
  val instanceConnectionName = MyConfig.get("google.cloudsql.instance-name")
  val username = MyConfig.get("google.cloudsql.username")
  val password = MyConfig.get("google.cloudsql.password")

  val jdbcUrl = String.format(
    "jdbc:mysql://google/%s?cloudSqlInstance=%s&"
      + "socketFactory=com.google.cloud.sql.mysql.SocketFactory",
    databaseName,
    instanceConnectionName);

  val connection = DriverManager.getConnection(jdbcUrl, username, password);
}

trait CloudSQLStorable{
  def SaveToCloudSQL(): Boolean
}


