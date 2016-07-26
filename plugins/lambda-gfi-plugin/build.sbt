name := """gestalt-lambda-gfi-plugin"""

organization := "com.galacticfog"

version := "0.2.8-SNAPSHOT"

scalaVersion := "2.11.5"

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

enablePlugins(NewRelic)

resolvers ++= Seq(
  "gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
  "snapshots" at "http://scala-tools.org/repo-snapshots",
  "releases"  at "http://scala-tools.org/repo-releases",
	"Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
	"Mesosphere Repo" at "http://downloads.mesosphere.io/maven",
	"Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
	)

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
	"com.typesafe.akka" % "akka-actor_2.11" % "2.3.4",
	"com.galacticfog" %% "gestalt-lambda-io" % "0.3.0-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-lambda-plugin" % "0.2.1-SNAPSHOT" withSources(),
	"com.galacticfog" %% "gestalt-io" % "1.0.5-SNAPSHOT" withSources(),
	"org.slf4j" % "slf4j-api" % "1.7.10",
	"ch.qos.logback" % "logback-classic" % "1.1.2",
	"com.newrelic.agent.java" % "newrelic-api" % "3.29.0",
	"com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
	"mesosphere" %% "mesos-utils" % "0.28.0" withJavadoc()
)

assemblyMergeStrategy in assembly := {
  case "META-INF/MANIFEST.MF"         => MergeStrategy.discard
  case _        											=> MergeStrategy.first
}


//
// This bit here adds a mapping for the "package" task that will add our necessary ServiceLoader sauce to the JAR
//

unmanagedResourceDirectories in Compile += { baseDirectory.value / "META-INF/services/com.galacticfog.gestalt.lambda.plugin.LambdaAdapter" }

mappings in (Compile, packageBin) <+= baseDirectory map { base =>
	(base / "META-INF" / "services" / "com.galacticfog.gestalt.lambda.plugin.GFIAdapter") -> "META-INF/services/com.galacticfog.gestalt.lambda.plugin.GFIAdapter"
}

newrelicVersion := "3.29.0"

newrelicAppName := "Lambda-Test"
