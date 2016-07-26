import com.galacticfog.gestalt.lambda.config.DatabaseConfig
import com.galacticfog.gestalt.lambda.io.ScalikePostgresInfo
import com.galacticfog.gestalt.meta.play.utils.GlobalMeta
import org.apache.commons.dbcp.BasicDataSource
import org.flywaydb.core.Flyway
import play.api.Play.current
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.{Application, GlobalSettings, Logger => log}
import play.libs.Akka
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}
import scala.collection.JavaConverters._

import scala.util.{Failure, Success}

object GlobalWithoutMeta extends GlobalSettings with GlobalMeta {

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
    val bClean = current.configuration.getBoolean( "database.clean" ) getOrElse false
    val bMigrate = current.configuration.getBoolean( "database.migrate" ) getOrElse false
    if( bMigrate ) migrate( connection, bClean, databaseConfig.username, databaseConfig.password )

    sys.addShutdownHook( akkaShutdown )

    log.debug( "app started" )
  }

  def akkaShutdown = {
  }

  override def onStop( app : Application ): Unit = {
    akkaShutdown
  }

  def getDBConfig( name : String ) : DatabaseConfig = {
    log.debug( s"getDBConfig( $name )")

    current.configuration.getObject("database") match {
      case None =>
        throw new RuntimeException("FATAL: Database configuration not found.")
      case Some(config) => {
        val configMap = config.unwrapped.asScala.toMap
        DatabaseConfig(
          host = configMap("host").toString,
          db_name = configMap("dbname").toString,
          port = configMap("port").toString.toInt,
          username = configMap("username").toString,
          password = configMap("password").toString)
      }
    }
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



