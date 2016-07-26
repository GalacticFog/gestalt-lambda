name := """gestalt-lambda"""

version := "1.0.4-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

enablePlugins(DockerPlugin)
enablePlugins(NewRelic)

import com.typesafe.sbt.packager.docker._

dockerBaseImage := "galacticfog.artifactoryonline.com/gestalt-mesos-base:0.0.0-1cbe9134"

maintainer in Docker := "Brad Futch <brad@galacticfog.com>"

dockerUpdateLatest := true

dockerExposedPorts in Docker := Seq(9000)

dockerRepository := Some("galacticfog.artifactoryonline.com")

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
	"com.amazonaws" % "aws-java-sdk" % "1.10.44",
	"com.typesafe.play" %% "play-json" % "2.4.0-M2",
	"com.newrelic.agent.java" % "newrelic-api" % "3.29.0",
	"com.galacticfog" %% "gestalt-lambda-io" % "0.3.0-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-lambda-plugin" % "0.2.1-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-security-play" % "2.2.3-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-meta-sdk-scala" % "0.3.0-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-utils" % "0.0.1-SNAPSHOT" withSources()
)


resolvers ++= Seq(
	"scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
	"snapshots" at "http://scala-tools.org/repo-snapshots",
	"releases"  at "http://scala-tools.org/repo-releases",
	"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
	"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-releases-local"
)

import NativePackagerHelper._
mappings in Universal ++= directory("plugins")

val mesosLib = new File("/usr/local/lib/libmesos.dylib")
mappings in Universal += mesosLib -> "lib/libmesos.dylib"

newrelicConfig := (resourceDirectory in Compile).value / "newrelic.yml"
