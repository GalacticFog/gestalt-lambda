package com.galacticfog.lambda.scheduler

import java.util.UUID

import org.apache.mesos.{Protos, MesosSchedulerDriver}
import org.apache.mesos.Protos.FrameworkInfo
import play.api.{Logger, Play, Application, GlobalSettings}
import scala.collection.mutable
import scala.concurrent.Future
import scala.sys.SystemProperties
import scala.concurrent.duration._
import Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.util.Try

case class CmdTask(uuid: UUID, handlerMethod: String, jarUrl: String)

object Global extends GlobalSettings {

  lazy val driver: Try[MesosSchedulerDriver] = Try {
    Logger.info("creating LambdaScheduler")
    val scheduler = new LambdaScheduler

    val master = current.configuration.getString("master") getOrElse "localhost:5050"
    Logger.info(s"registering with mesos-master: ${master}")

    val schedulerHostname = current.configuration.getString("hostname") getOrElse java.net.InetAddress.getLocalHost.getHostName
    Logger.info(s"scheduler on: ${schedulerHostname}")

    val frameworkInfoBuilder = FrameworkInfo.newBuilder()
      .setName("gestalt-lambda-scheduler")
      .setFailoverTimeout(60.seconds.toMillis)
      .setUser("")
      .setCheckpoint(true)
      .setHostname(schedulerHostname)

    val frameworkInfo = frameworkInfoBuilder.build()
    Logger.info(s"scheduler on: ${schedulerHostname}")
    val implicitAcknowledgements = false

    new MesosSchedulerDriver( scheduler, frameworkInfo, master, implicitAcknowledgements )
  }

  val taskQueue = new mutable.Queue[CmdTask]

  override def onStart(app: Application): Unit = {
    Logger.info("onStart")
    driver foreach { d =>
      Logger.info("starting driver")
      Future { d.run } map println
    }
  }

  override def onStop(app: Application): Unit = {
    Logger.info("onStop")
    driver foreach {
      Logger.info("stopping driver")
      _.stop()
    }
  }
}
