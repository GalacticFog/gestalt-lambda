package com.galacticfog.gestalt.lambda.config

import play.api.libs.json.Json

case class
DatabaseConfig(
  host : String,
  port : Int,
  db_name : String,
  username : String,
  password : String
)

object DatabaseConfig {
  implicit val dbConfigFormat = Json.format[DatabaseConfig]
}

