import com.typesafe.sbt.packager.docker._

name := """lambda-scheduler"""

version := "1.0-SNAPSHOT"

maintainer in Docker := "Chris Baker <chris@galacticfog.com>"

dockerUpdateLatest := true

// dockerRepository := Some("galacticfog.artifactoryonline.com")

dockerRepository := Some("192.168.200.20:5000")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

resolvers += "Mesosphere Repo" at "http://downloads.mesosphere.io/maven"

libraryDependencies += "mesosphere" %% "mesos-utils" % "0.26.0" withJavadoc()

libraryDependencies ++= Seq(
  ws,
  specs2 % Test
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
