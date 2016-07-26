addSbtPlugin("com.galacticfog" % "sbt-vertx" % "1.0.0")


resolvers ++= Seq(
		"scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
		"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
		"Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
		"Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
		)
