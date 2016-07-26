name := """test-post"""

version := "1.0.0-SNAPSHOT"

organization := "com.galacticfog"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
	"com.fasterxml.jackson.core" % "jackson-core" % "2.2.2",
	"com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.2",
	"com.fasterxml.jackson.core" % "jackson-databind" % "2.2.2",
	"io.vertx" % "vertx-core" % "2.1.6",
	"io.vertx" % "vertx-platform" % "2.1.6",
	"io.vertx" % "lang-scala_2.11" % "1.1.0-SNAPSHOT"
)

resolvers ++= Seq(
	"scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
	"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
	"Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
	DefaultMavenRepository
)

vertxSettings
