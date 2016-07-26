name := """gestalt-lambda-io"""

organization := "com.galacticfog"

version := "0.3.0-SNAPSHOT"

scalaVersion := "2.11.5"

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishTo := Some("Artifactory Realm" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local/")

resolvers ++= Seq(
  "gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
  "snapshots" at "http://scala-tools.org/repo-snapshots",
  "releases"  at "http://scala-tools.org/repo-releases")

credentials ++= {
  (for {
    realm <- sys.env.get("GESTALT_RESOLVER_REALM")
    username <- sys.env.get("GESTALT_RESOLVER_USERNAME")
    resolverUrlStr <- sys.env.get("GESTALT_RESOLVER_URL")
    resolverUrl <- scala.util.Try{url(resolverUrlStr)}.toOption
    password <- sys.env.get("GESTALT_RESOLVER_PASSWORD")
  } yield {
    Seq(Credentials(realm, resolverUrl.getHost, username, password))
  }) getOrElse(Seq())
}

resolvers ++= {
  sys.env.get("GESTALT_RESOLVER_URL") map {
    url => Seq("gestalt-resolver" at url)
  } getOrElse(Seq())
}

//
// Adds project name to prompt like in a Play project
//
shellPrompt in ThisBuild := { state => "\033[0;36m" + Project.extract(state).currentRef.project + "\033[0m] " }

// ----------------------------------------------------------------------------
// This adds a new command to sbt: type 'flyway' to execute
// 'flywayClean' and 'flywayMigrate' in sequence
// ----------------------------------------------------------------------------
lazy val FlywayRebuild = Command.command("flyway") { state =>
	"flywayClean" :: "flywayMigrate" :: state
}

commands += FlywayRebuild


//------------------------
// Generate Data Model
//------------------------

lazy val GenDataModel = Command.command("generateModel") 
{ state =>
"scalikejdbcGenForce lambda LambdaRepository" :: 
"scalikejdbcGenForce result ResultRepository" :: 
state
}

commands += GenDataModel


// ----------------------------------------------------------------------------
// ScalikeJDBC
// ----------------------------------------------------------------------------

scalikejdbcSettings

libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "2.2.3"

libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-test"   % "2.2.3"   % "test"

libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-config" % "2.2.3" % "test"


// ----------------------------------------------------------------------------
// PostgreSQL
// ----------------------------------------------------------------------------

libraryDependencies += "org.postgresql" % "postgresql" % "9.3-1102-jdbc4"


// ----------------------------------------------------------------------------
// Play JSON
// ----------------------------------------------------------------------------

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.0-M2"


// ----------------------------------------------------------------------------
// Specs 2
// ----------------------------------------------------------------------------

libraryDependencies += "junit" % "junit" % "4.12" % "test"

libraryDependencies += "org.specs2" %% "specs2-junit" % "2.4.15" % "test"

libraryDependencies += "org.specs2" %% "specs2-core" % "2.4.15" % "test"


// ----------------------------------------------------------------------------
// Flyway
// ----------------------------------------------------------------------------

libraryDependencies += "com.h2database" % "h2" % "1.4.186"

libraryDependencies += "org.flywaydb" % "flyway-core" % "3.2.1"


// ----------------------------------------------------------------------------
// Flyway Plugin Settings
// ----------------------------------------------------------------------------

seq(flywaySettings: _*)

flywayUser := sys.env.get( "DB_USER" ) getOrElse "dbUser"

flywayPassword := sys.env.get( "DB_PASSWORD" ) getOrElse "dbS3cr3t"

val hostname = sys.env.get( "DB_HOST" ) getOrElse "localhost"

flywayUrl := s"jdbc:postgresql://$hostname:5432/gestaltlambda"



// ----------------------------------------------------------------------------
// Logging
// ----------------------------------------------------------------------------

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.10"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
