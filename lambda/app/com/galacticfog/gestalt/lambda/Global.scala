package com.galacticfog.gestalt.lambda

import com.galacticfog.gestalt.lambda.config.DatabaseConfig
import com.galacticfog.gestalt.lambda.io.ScalikePostgresInfo
import org.apache.commons.dbcp.BasicDataSource
import org.flywaydb.core.Flyway
import play.api.Play.current
import play.api.libs.json.{JsSuccess, Json, JsError}
import play.api.{Application, GlobalSettings, Logger => log}
import play.libs.Akka
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}

import scala.util.{Success, Failure}

object Global extends GlobalSettings {

  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = false,
    singleLineMode = false,
    printUnprocessedStackTrace = false,
    stackTraceDepth= 15,
    logLevel = 'connection,
    warningEnabled = false,
    warningThresholdMillis = 3000L,
    warningLogLevel = 'warn
  )

  override def onStart( app : Application ) : Unit = {

    val databaseConfig = getDBConfig( "database" )
    val connection = initDB( databaseConfig )

    //now check if we're doing migration and cleaning
    //TODO : FIX
    val bClean = current.configuration.getBoolean( "database.clean" ) getOrElse false
    val bMigrate = current.configuration.getBoolean( "database.migrate" ) getOrElse false
    if( bMigrate ) migrate( connection, bClean, databaseConfig.username, databaseConfig.password )

    sys.addShutdownHook( akkaShutdown )

    log.debug( "app started" )
  }

  def akkaShutdown = {
    LambdaFramework.shutdown()
    Akka.system.shutdown()
    Akka.system.awaitTermination()
    log.debug( "Akka actorsystem ::shutdown()" )
  }

  override def onStop( app : Application ): Unit = {
    akkaShutdown
  }

  def getDBConfig( name : String ) : DatabaseConfig = {
    log.debug( s"getDBConfig( $name )")

    val hostname = sys.env.getOrElse( "LAMBDA_DATABASE_HOSTNAME", "localhost" )
    val port = sys.env.getOrElse( "LAMBDA_DATABASE_PORT", "5432" ).toInt
    val dbName = sys.env.getOrElse( "LAMBDA_DATABASE_NAME", "gestaltlambda" )
    val dbUser = sys.env.getOrElse( "LAMBDA_DATABASE_USER", "gestaltdev" )
    val dbPassword = sys.env.getOrElse( "LAMBDA_DATABASE_PASSWORD", "M8keitw0rk" )

    println( "Database Connection Info : " )
    println( "\t hostname : " + hostname )
    println( "\t port     : " + port )
    println( "\t dbName   : " + dbName )
    println( "\t dbUser   : " + dbUser )
    //println( "\t dbPass   : " + dbPassword )

    new DatabaseConfig( hostname, port, dbName, dbUser, dbPassword )
  }

  def initDB( dbConfig : DatabaseConfig ) : ScalikePostgresInfo = {
    log.debug( "initDB()" )
    new ScalikePostgresInfo( dbConfig.host, dbConfig.port, dbConfig.db_name, dbConfig.username, dbConfig.password )
  }

  def getDataSource( connection : ScalikePostgresInfo ) = {
    val ds = new BasicDataSource()
    ds.setDriverClassName(connection.driver)
    ds.setUsername(connection.username.get)
    ds.setPassword(connection.password.get)
    ds.setUrl(connection.url())
    log.debug("url: " + ds.getUrl)
    ds
  }

  def migrate( connection : ScalikePostgresInfo, bClean : Boolean, username : String, password : String ) = {
    log.debug( "migrate()" )
    val fly = new Flyway()
    val dataSource = getDataSource( connection )
    fly.setDataSource( dataSource )
    if( bClean ) fly.clean()
    fly.migrate()
  }

}



