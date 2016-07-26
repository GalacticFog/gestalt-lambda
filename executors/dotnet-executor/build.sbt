name := """lambda-dotnet-alt-executor"""

version := "1.2.0-SNAPSHOT"

mainClass in (Compile, packageBin) := Some("com.galacticfog.lambda.executor.DotNetExecutor")

lazy val root = (project in file("."))

scalaVersion := "2.11.6"

resolvers ++= Seq(
	"Mesosphere Repo" at "http://downloads.mesosphere.io/maven",
	"Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
	"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local"
)
	

libraryDependencies ++= Seq(
	"com.galacticfog" %% "gestalt-utils" % "0.0.1-SNAPSHOT" withSources(),
	"org.specs2" %% "specs2-core" % "3.7" % "test",
	"com.typesafe.play" %% "play-json" % "2.4.0-M2",
	"mesosphere" %% "mesos-utils" % "0.28.0"
)

scalacOptions in Test ++= Seq("-Yrangepos")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._
import NativePackagerHelper._
mappings in Universal += file("install-coreclr.sh") -> "local/install-coreclr.sh"

dockerBaseImage := "galacticfog.artifactoryonline.com/gestalt-mesos-base:0.0.0-d5747ff7"

dockerCommands := dockerCommands.value.flatMap{
  case cmd@Cmd("ADD",_) => List(
    cmd, 
		Cmd("RUN","apt-get -y install sudo"),
		Cmd("RUN", "echo \"daemon:daemon\" | chpasswd && adduser daemon sudo"),
		Cmd("RUN", "echo \"daemon ALL=(ALL) NOPASSWD: ALL\" >> /etc/sudoers"),
		Cmd("USER", "daemon"),

		Cmd("RUN", "sudo sh -c 'echo \"deb http://llvm.org/apt/jessie/ llvm-toolchain-jessie-3.6 main\" > /etc/apt/sources.list.d/llvm-toolchain.list'"),
		Cmd("RUN", "sudo sh -c 'echo \"deb-src http://llvm.org/apt/jessie/ llvm-toolchain-jessie-3.6 main\" > /etc/apt/sources.list.d/llvm-toolchain1.list'"),
		Cmd("RUN", "wget -O - http://llvm.org/apt/llvm-snapshot.gpg.key|sudo apt-key add -"),
		Cmd("RUN", "sudo apt-get update && sudo apt-get -y install liblldb-3.6"),

		Cmd("RUN", "sudo sh -c 'echo \"deb [arch=amd64] http://apt-mo.trafficmanager.net/repos/dotnet/ trusty main\" > /etc/apt/sources.list.d/dotnetdev.list'"),
		Cmd("RUN", "sudo apt-key adv --keyserver apt-mo.trafficmanager.net --recv-keys 417A0893"),
		Cmd("RUN", "sudo apt-get update"),
		Cmd("RUN", "sudo apt-get install -y dotnet"),
		Cmd("USER", "root"),
		Cmd("RUN", "chown -R root:daemon /usr/sbin")
  )
  case other => List(other)
}

//Cmd("ADD", "install-coreclr.sh local/"),

maintainer := "Brad Futch <brad@galacticfog.com>"

dockerUpdateLatest := true

dockerRepository := Some("galacticfog.artifactoryonline.com")

