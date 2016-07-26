name := """gestalt-vertx"""

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
	"com.fasterxml.jackson.core" % "jackson-core" % "2.2.2",
	"com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.2",
	"com.fasterxml.jackson.core" % "jackson-databind" % "2.2.2",
	"io.vertx" % "vertx-core" % "3.1.0",
	"com.galacticfog" %% "gestalt-lambda-plugin" % "0.0.1-SNAPSHOT" withSources()
)

resolvers ++= Seq(
	"scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
	"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
	"Atlassian Releases" at "https://maven.atlassian.com/public/"
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

assemblyMergeStrategy in assembly := {
	case "META-INF/MANIFEST.MF"         => MergeStrategy.discard
	case _                              => MergeStrategy.first
}

//
// This bit here adds a mapping for the "package" task that will add our necessary ServiceLoader sauce to the JAR
//

unmanagedResourceDirectories in Compile += { baseDirectory.value / "META-INF/services/com.galacticfog.gestalt.lambda.plugin.LambdaAdapter" }

mappings in (Compile, packageBin) <+= baseDirectory map { base =>
  (base / "META-INF" / "services" / "com.galacticfog.gestalt.lambda.plugin.LambdaAdapter") -> "META-INF/services/com.galacticfog.gestalt.lambda.plugin.LambdaAdapter"
}
