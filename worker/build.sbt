name := """gestalt-lambda-worker"""

organization := "com.galacticfog"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.5"

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishTo := Some("Galacticfog Snapshot" at "https://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local")

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

libraryDependencies ++= Seq (
	"com.galacticfog" %% "gestalt-utils" % "0.0.1-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-lambda-io" % "0.0.1-SNAPSHOT" withSources(),
	"org.slf4j" % "slf4j-api" % "1.7.10",
	"ch.qos.logback" % "logback-classic" % "1.1.2",
	"com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
)

