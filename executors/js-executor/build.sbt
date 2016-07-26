name := """lambda-javascript-executor"""

version := "1.2.0-SNAPSHOT"

mainClass in (Compile, packageBin) := Some("com.galacticfog.lambda.executor.JavaScriptExecutor")

lazy val root = (project in file("."))

scalaVersion := "2.11.6"

resolvers ++= Seq(
	"Mesosphere Repo" at "http://downloads.mesosphere.io/maven",
	"Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
)

libraryDependencies ++= Seq(
	"org.specs2" %% "specs2-core" % "3.7" % "test",
	"org.slf4j" % "slf4j-api" % "1.7.10",
	"ch.qos.logback" % "logback-classic" % "1.1.2",
	"com.typesafe.play" %% "play-json" % "2.4.0-M2",
	"mesosphere" %% "mesos-utils" % "0.28.0",
	"com.groupon.mesos" % "jesos" % "1.5.4-SNAPSHOT" withSources()
)

scalacOptions in Test ++= Seq("-Yrangepos")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._

dockerBaseImage := "galacticfog.artifactoryonline.com/gestalt-mesos-base:0.0.0-d5747ff7"

maintainer := "Brad Futch <brad@galacticfog.com>"

dockerUpdateLatest := true

dockerRepository := Some("galacticfog.artifactoryonline.com")
