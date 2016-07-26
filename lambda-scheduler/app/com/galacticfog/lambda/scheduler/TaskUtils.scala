package com.galacticfog.lambda.scheduler

import java.io.File

import com.google.protobuf.ByteString
import org.apache.mesos.Protos.{ContainerInfo, CommandInfo}
import org.apache.mesos.Protos.ContainerInfo.DockerInfo
import org.apache.mesos._

import scala.collection.JavaConverters._

/*
    A lot of this was ripped off from the scala-based scheduler for RENDLER
    They didn't use containers and I haven't tested it yet, so all bets are off on this working as is.
    Also, lots of refactoring will be necessary due to the fact that our lambdas
    - don't all have the same container (i.e., the same executor)
    - don't all have the same CPU and Memory requirement
 */

trait TaskUtils {

  val TASK_CPUS = 0.1
  val TASK_MEM = 32.0

  lazy val echoExecutorContainer =
    Protos.CommandInfo.ContainerInfo.newBuilder()
      .setImage("galacticfog.artifactoryonline.com/lambda-echo-executor")
      .build()

  def makeTaskPrototype(id: String, offer: Protos.Offer): Protos.TaskInfo =
    Protos.TaskInfo.newBuilder
      .setTaskId(Protos.TaskID.newBuilder.setValue(id))
      .setName("")
      .setSlaveId(offer.getSlaveId)
      .addAllResources(
        Seq(
          scalarResource("cpus", TASK_CPUS),
          scalarResource("mem", TASK_MEM)
        ).asJava
      )
      .build

  protected def scalarResource(name: String, value: Double): Protos.Resource =
    Protos.Resource.newBuilder
      .setType(Protos.Value.Type.SCALAR)
      .setName(name)
      .setScalar(Protos.Value.Scalar.newBuilder.setValue(value))
      .build

  def echoExecutor(jarUrl: String): Protos.ExecutorInfo = {
    val command = Protos.CommandInfo.newBuilder
      .setContainer(echoExecutorContainer)
      .addAllUris(Seq(
        CommandInfo.URI.newBuilder.setValue(jarUrl).build
      ).asJava)
    Protos.ExecutorInfo.newBuilder
      .setExecutorId(Protos.ExecutorID.newBuilder.setValue("crawl-executor"))
      .setName("Crawler")
      .setCommand(command)
      .build
  }

  def makeEchoTask(id: String,
                   handlerMethod: String,
                   jarUrl: String,
                   offer: Protos.Offer): Protos.TaskInfo =
    makeTaskPrototype(id, offer).toBuilder
      .setName(s"echo_$id")
      .setExecutor(echoExecutor(jarUrl))
      .setData(ByteString.copyFromUtf8(handlerMethod))
      .build

  def maxTasksForOffer(
    offer: Protos.Offer,
    cpusPerTask: Double = TASK_CPUS,
    memPerTask: Double = TASK_MEM): Int = {
    var count = 0
    var cpus = 0.0
    var mem = 0.0

    for (resource <- offer.getResourcesList.asScala) {
      resource.getName match {
        case "cpus" => cpus = resource.getScalar.getValue
        case "mem"  => mem = resource.getScalar.getValue
        case _      => ()
      }
    }

    while (cpus >= TASK_CPUS && mem >= TASK_MEM) {
      count = count + 1
      cpus = cpus - TASK_CPUS
      mem = mem - TASK_MEM
    }

    count
  }

  def isTerminal(state: Protos.TaskState): Boolean = {
    import Protos.TaskState._
    state match {
      case TASK_FINISHED | TASK_FAILED | TASK_KILLED | TASK_LOST =>
        true
      case _ =>
        false
    }
  }

}
