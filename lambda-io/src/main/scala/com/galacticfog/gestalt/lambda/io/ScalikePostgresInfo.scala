package com.galacticfog.gestalt.lambda.io

import scalikejdbc._

class ScalikePostgresInfo(
    host: String, port: Int, 
    database: String, user: String, password: String, timeoutMs: Long = 5000L)
      extends PostgresJdbcInfo(host, Some(port), database, Some(user), Some(password)) {

  val settings = ConnectionPoolSettings(
    connectionTimeoutMillis = timeoutMs
    /*,validationQuery = "select 1 from organization;"*/)  
  
  /* This magically 'opens' the connection */
  Class.forName(driver)
  ConnectionPool.singleton(url(), user, password, settings)
}

object ScalikePostgresInfo {
  def apply(host: String, port: Int = 5432, database: String, 
      user: String, password: String, timeoutMs: Long = 5000L) = {
    new ScalikePostgresInfo(host, port, database, user, password)
  }
  
}