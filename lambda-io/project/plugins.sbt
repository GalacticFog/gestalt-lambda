addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.5.0")


// Driver needed here for scalike mapper.

libraryDependencies += "org.postgresql" % "postgresql" % "9.3-1102-jdbc4"

addSbtPlugin("org.scalikejdbc" %% "scalikejdbc-mapper-generator" % "2.2.3")


//
// Flyway
//

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "3.1")

resolvers += "Flyway" at "http://flywaydb.org/repo"

