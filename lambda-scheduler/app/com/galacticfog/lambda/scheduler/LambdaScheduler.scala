package com.galacticfog.lambda.scheduler

import java.util

import org.apache.mesos.Protos._
import org.apache.mesos.{SchedulerDriver, Scheduler}
import play.api.Logger

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

import play.api.libs.concurrent.Execution.Implicits.defaultContext

class LambdaScheduler extends Scheduler with TaskUtils {

  val offers = new mutable.Queue[Offer]()

  private[this] def getResource(offer: Offer, name: String): String = {
    offer.getResourcesList.asScala.find(_.getName == name) map {_.getScalar.getValue.toString} getOrElse ""
  }

  private[this] val log = Logger.logger

  override def disconnected(driver: SchedulerDriver): Unit =
    println("Disconnected from the Mesos master...")

  override def reregistered(driver: SchedulerDriver, master: MasterInfo): Unit = {
    log.info("Re-registered to %s".format(master))
  }

  override def slaveLost(driver: SchedulerDriver, slave: SlaveID): Unit =
    println("SLAVE LOST: [${slaveId.getValue}]")

  override def error(driver: SchedulerDriver, s: String): Unit = {
    log.error(s"Error: ${s}\nCommitting suicide!")

    // Asynchronously call sys.exit() to avoid deadlock due to the JVM shutdown hooks
    Future(sys.exit(9)).onFailure {
      case NonFatal(t) => log.error("Exception while committing suicide", t)
    }
  }

  override def statusUpdate(driver: SchedulerDriver, status: TaskStatus): Unit = {
    log.info("Received status update for task %s: %s (%s)"
      .format(status.getTaskId.getValue, status.getState, status.getMessage))
    println("Output: " + status.getData.toString("UTF-8"))
    driver.acknowledgeStatusUpdate(status)
  }

  // this is a framework message from our executor, returning the output of a lambda execution
  // Akka-message it back to the sender so they can serve it up, etc.
  override def frameworkMessage(driver: SchedulerDriver, executor: ExecutorID, slave: SlaveID, mesg: Array[Byte]): Unit = {
    log.info("Received framework message %s %s %s ".format(executor, slave, mesg))
  }

  override def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]): Unit = {
    // currently, this method actually sends queued lambda executions against this offer
    // instead, it should cache these offers so that the lambda execution requests can be handled as they arrive
    // unless there are unhandled launches, in which case, they should be dequeued and launched on this offer
    // pain in the ass...
    //
    // i'm not sure whether one lambda execution is one task (i think not) or
    // one task is one residency of the lambda (supporting multiple executions) (i think so)
    // the latter means we don't equate TaskID and GestaltLambdaExecutionID; we'll need to generate a separate
    // ID for the specific execution of a particular lambda, in order to map the framework message response back to the
    // appropriate place in this scheduler service (probably via Akka response to some stored sender)
    println(if (Global.taskQueue.isEmpty) "No queued tasks" else "Queued tasks:")
    Global.taskQueue.foreach {t => println(t.handlerMethod)}
    for (offer <- offers.asScala) {
      println(s"Got resource offer [cpu: ${getResource(offer, "cpus")}, mem: ${getResource(offer, "mem")}]")

      val numTasks = maxTasksForOffer(offer) min Global.taskQueue.size

      val tasks = for {
        _ <- 1 to numTasks
        t = Global.taskQueue.dequeue()
        tsk = makeEchoTask(t.uuid.toString, t.handlerMethod, t.jarUrl, offer)
      } yield tsk

      val status = if (tasks.nonEmpty) {
        println(s"launching task on offer [${offer.getId}]")
        driver.launchTasks(Seq(offer.getId).asJava, tasks.asJava)
      }
      else {
        println(s"declining offer [${offer.getId.getValue}]")
        driver.declineOffer(offer.getId)
      }
    }
  }

  override def offerRescinded(schedulerDriver: SchedulerDriver, offer: OfferID): Unit = {
    // i think this happens when we attempt to launch against an old request
    // i imagine that's bad news; it means we've probably entered into a high-latency scenario
    // let's optimize that if we run into it; in the meantime, it means you need to put the lambda execution into
    // some queue where it can be handled on the next offer
    log.info("Offer %s rescinded".format(offer))
  }

  override def registered(driver: SchedulerDriver, frameworkID: FrameworkID, master: MasterInfo): Unit = {
    val host = master.getHostname
    val port = master.getPort
    println(s"Registered with Mesos master [$host:$port]")
  }

  // this means our specialized
  override def executorLost(driver: SchedulerDriver, executor: ExecutorID, slave: SlaveID, i: Int): Unit = {
    log.info(s"Lost executor $executor slave $i")
  }
}
