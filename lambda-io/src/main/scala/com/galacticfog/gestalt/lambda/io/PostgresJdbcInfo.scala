package com.galacticfog.gestalt.lambda.io

class PostgresJdbcInfo(
    host: String, port: Option[Int] = Some(5432), database: String, 
    username: Option[String], password: Option[String]) 
    extends JdbcConnectionInfo(
        "postgresql", "org.postgresql.Driver",
        host, port, database, username, password) {
}

object PostgresJdbcInfo {
  def apply(host: String, port: Option[Int] = Some(5432), database: String, 
      username: Option[String], password: Option[String]) = {
    new PostgresJdbcInfo(host, port, database, username, password)
  }
}

