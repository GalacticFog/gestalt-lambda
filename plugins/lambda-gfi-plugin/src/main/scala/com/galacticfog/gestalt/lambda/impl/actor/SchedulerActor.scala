package com.galacticfog.gestalt.lambda.impl.actor


import java.util.UUID

import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.Gestalt
import com.galacticfog.gestalt.lambda.impl.actor.GFIMessages._
import com.galacticfog.gestalt.lambda.impl.{ExecutorCache, GFILambdaInfo}
import com.galacticfog.gestalt.lambda.io.domain.{LambdaDao, LambdaEvent}
import com.google.protobuf.ByteString
import org.apache.mesos.Protos.ExecutorID
import org.apache.mesos.Protos.FrameworkID
import org.apache.mesos.Protos.MasterInfo
import org.apache.mesos.Protos.Offer
import org.apache.mesos.Protos.Offer.Operation.Reserve
import org.apache.mesos.Protos.OfferID
import org.apache.mesos.Protos.SlaveID
import org.apache.mesos.Protos.TaskStatus
import org.apache.mesos._
import org.apache.mesos.Protos.Resource.ReservationInfo
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}
import scala.concurrent.duration._
import org.apache.mesos.Protos._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import com.newrelic.api.agent.Trace


/**
 * SchedulerActor
 */

case class RunningLambda(
  sender : ActorRef,
  lambda : GFILambdaInfo,
  event : LambdaEvent,
  executionId : String,
  executorId : String,
  taskId : Protos.TaskID,
  launch : DateTime,
  env : Future[Map[String, String]],
  queueMillis : Long,
  start : Option[DateTime] = None,
  finish : Option[DateTime] = None
)

case class QueuedLambda(
  sender : ActorRef,
  lambda : GFILambdaInfo,
  event : LambdaEvent,
  queueTime : DateTime,
  uuid : String,
  env : Future[Map[String, String]],
  creds : Option[String]
)

case class LambdaTask( lambda : QueuedLambda, task : TaskInfo )

case class OfferMatch(
  offer : Protos.Offer,
  lambdas : mutable.Queue[LambdaTask] = mutable.Queue[LambdaTask](),
  spentRam : Double = 0.0,
  spentCpu : Double = 0.0
)

class SchedulerActor extends Actor with Scheduler with ActorLogging {

  val logger = LoggerFactory.getLogger( SchedulerActor.this.getClass )

  val lambdaMap : mutable.HashMap[Protos.TaskID, RunningLambda] = new mutable.HashMap[Protos.TaskID, RunningLambda]()
  //val hotCache = new ExecutorCache()

  var taskActor : ActorRef = null
  var offerActor : ActorRef = null

  import context._

  @Trace(dispatcher=true)
  def receive = LoggingReceive { handleRequests }

  var driver : MesosSchedulerDriver = null
  var role : String = null


  override def preStart() = {
    logger.debug("creating LambdaScheduler")

    //TODO : remove default
    val mesosConnectionString = sys.env.getOrElse( "MESOS_MASTER_CONNECTION", "192.168.200.20:5050" )
    val mesosRole = sys.env.getOrElse( "MESOS_ROLE", "*" )
    val schedulerName = sys.env.getOrElse( "SCHEDULER_NAME", "lambda-scheduler" )

    role = mesosRole

    logger.debug( "mesos master : " + mesosConnectionString )
    logger.debug( "mesos role   : " + mesosRole )
    logger.debug( "mesos name   : " + schedulerName )


    //TODO : not sure the scheduler hostname is necessary, it seems to work fine without it and getting this info is a
    //little tricky with the mesos + docker setup
    //.setHostname(schedulerHostname)

    val frameworkInfoBuilder = FrameworkInfo.newBuilder()
      .setName( schedulerName )
      .setFailoverTimeout(60) //seconds
      //TODO : make env
      .setUser("root")
      .setRole( role )
      .setCheckpoint(false)

    val frameworkInfo = frameworkInfoBuilder.build()

    val implicitAcknowledgements = true

    driver = new MesosSchedulerDriver( this, frameworkInfo, mesosConnectionString, implicitAcknowledgements )

    taskActor = context.actorOf( TaskActor.props( role ) )
    offerActor = context.actorOf( OfferActor.props( driver, taskActor ) )

    //NOTE : This will block the thread indefinitely if not run in a separate thread execution (hence the Future).
    // - this will result in this actor no longer responding to messages and not shutting down ever

    Future { driver.run } map { d =>
      logger.debug( "Driver::DONE" )
    }
  }

  def killExecutor( lambdaId : String, uuid : String, executorId : Protos.ExecutorID, slaveId : Protos.SlaveID, schedDriver : Option[SchedulerDriver] = None ) : Unit = {
    logger.debug( s"killing executor (${executorId.getValue})")

    val message = "KILL"

    schedDriver match {
      case Some(s) => {
        s.sendFrameworkMessage( executorId, slaveId, message.getBytes )
      }
      case None => {
        driver.sendFrameworkMessage( executorId, slaveId, message.getBytes )
      }
    }
    //hotCache.remove( lambdaId, uuid )
  }

  val handleRequests : Receive = {

    case ShutdownScheduler => {
      logger.debug( "SchedulerActor::Shutdown")
      driver.stop()
    }

    case TestMessage => {
      logger.debug( s"Test()" )

      //system.scheduler.scheduleOnce( TICK_TIME, self, TestMessage )
    }

    case InvalidateCache( lambda ) => {
      logger.debug( "InvalidateCache" )

      //hotCache.invalidateCache( lambda.id.get )
    }

    case TimeoutLambda( uuid ) => {
      logger.debug( "SchedulerActor::TimeoutLambda" )
    }

    case InvokeLambda( data, event, uuid, env, qSender, creds ) => {
      logger.debug( s"SchedulerActor::InvokeLambda( $uuid )" )

      val senderActor = qSender getOrElse sender
      taskActor ! QueueLambda( data, event, uuid, env, senderActor, creds )
    }

    case RequestOffer => {
      offerActor ! RequestOffer
    }

    case LaunchTasks( container ) => {

      offerActor ! RemoveOffer( container.offer )

      container.lambdas.foreach{ lambdaTask =>

        val lambda = lambdaTask.lambda
        val task = lambdaTask.task

        //TODO : put this somewhere
        val queueMillis = DateTime.now.getMillis - lambda.queueTime.getMillis

        val runningLambda = new RunningLambda(
          sender = lambda.sender,
          lambda = lambda.lambda,
          event = lambda.event,
          executionId = lambda.uuid,
          executorId = lambda.uuid,
          env = lambda.env,
          taskId = task.getTaskId,
          queueMillis = queueMillis,
          launch = DateTime.now
        )

        lambdaMap += ( task.getTaskId -> runningLambda )
      }

      val selectedTasks = container.lambdas.map( _.task )
      val offer = container.offer

      driver.launchTasks( Seq( offer.getId ).asJava, selectedTasks.asJava )
    }
  }

  def queuedCPU( lambdas : mutable.Queue[QueuedLambda] ) : Double = {
    lambdas.foldLeft(0.0)( (tot,cpu) => tot + cpu.lambda.cpus )
  }

  def queuedRAM( lambdas : mutable.Queue[QueuedLambda] ) : Double = {
    lambdas.foldLeft(0.0)( (tot,ram) => tot + ram.lambda.memorySize )
  }

  private[this] def getTaskResourceValue(task: TaskInfo, name: String): String = {
    task.getResourcesList.asScala.find(_.getName == name) map {_.getScalar.getValue.toString} getOrElse ""
  }

  private[this] def getTaskResourceTotal(task: TaskInfo, name: String): Double = {
    task.getResourcesList.asScala.filter(_.getName == name).foldLeft(0.0)( (tot,res) => tot + res.getScalar.getValue )
  }

  private[this] def getTaskResources(task: TaskInfo, name: String) : Seq[Protos.Resource] = {
    task.getResourcesList.asScala.filter(_.getName == name)
  }


  override def disconnected(driver: SchedulerDriver): Unit =
    logger.debug("Disconnected from the Mesos master...")

  override def reregistered(driver: SchedulerDriver, master: MasterInfo): Unit = {
    logger.debug("Re-registered to %s".format(master))
  }

  override def slaveLost(driver: SchedulerDriver, slave: SlaveID): Unit =
    logger.debug("SLAVE LOST: [${slaveId.getValue}]")

  override def error(driver: SchedulerDriver, s: String): Unit = {
    logger.debug(s"Error: ${s}\nCommitting suicide!")

    // Asynchronously call sys.exit() to avoid deadlock due to the JVM shutdown hooks
    Future(sys.exit(9)).onFailure {
      case NonFatal(t) => logger.debug("Exception while committing suicide", t)
    }
  }

  override def statusUpdate(schedDriver: SchedulerDriver, status: TaskStatus): Unit = {
    logger.debug("Received status update for task %s: %s (%s)"
      .format(status.getTaskId.getValue, status.getState, status.getMessage))

    //NOTE : it's possible to receive a status that we don't have mapped in the event
    //       that the status is about a task we never started (perhaps due to a stale offer?)
    //TODO : what do we do about that status update in the case we can't use it?

    lambdaMap.get( status.getTaskId ).map { running =>
      status.getState match {
        case TaskState.TASK_FINISHED => {
          val finished = Some( DateTime.now )
          val updated = running.copy( finish = finished )

          //TODO : why were we remembering these instead of just removing them?
          //lambdaMap.update( status.getTaskId, updated )

          logger.debug( "LAMBDA TIMES : \n" +
            "Queued   : " + (updated.queueMillis.toDouble / 1000.0) + " seconds \n" +
            "Launch   : " + updated.launch.toLocalDateTime.toString + "\n" +
            "Started  : " + updated.start.get.toLocalDateTime.toString + "\n" +
            "Finished : " + updated.finish.get.toLocalDateTime.toString + "\n"
          )

          /*
          logger.debug( "Seaching for cache for : [" + updated.lambda.lambdaId.get + "," + updated.executorId + "]")
          hotCache.get( updated.lambda.lambdaId.get, updated.executorId ) match {
            case Some( s ) => {
              hotCache( updated.lambda.lambdaId.get, updated.executorId ) = s.copy( bDone = true, lastRan = DateTime.now, taskId = Some(status.getTaskId), driver = Some(schedDriver), executor = Some(status.getExecutorId), slaveId = Some(status.getSlaveId) )
            }
            case None => {
              logger.error( "Our cache got out of sync, how can that be?" )
              //throw new Exception( "Our cache got out of sync, how can that be?" )
            }
          }
          */

          //TODO : figure out how to work with the responses
          log.debug( "Sending complete message to : " + running.sender.path.toSerializationFormat )
          running.sender ! status.getMessage

          lambdaMap -= status.getTaskId

          killExecutor( running.lambda.lambdaId.get, running.executorId, status.getExecutorId, status.getSlaveId, Some(schedDriver) )

        }
        case TaskState.TASK_ERROR | TaskState.TASK_FAILED | TaskState.TASK_KILLED => {
          logger.error( "ERROR : " + status.getTaskId.getValue )
          logger.error( "Output: " + status.getData.toString( "UTF-8" ) )
          logger.error( "Message: " + status.getMessage )


          //TODO : should we invalidate the cache on failure??
          killExecutor( running.lambda.lambdaId.get, running.executorId, status.getExecutorId, status.getSlaveId, Some(schedDriver) )

          lambdaMap -= status.getTaskId
          running.sender ! status.getMessage
          //TODO : anything else??
        }
        case TaskState.TASK_RUNNING => {
          val started = Some( DateTime.now )
          val updated = running.copy( start = started )
          lambdaMap.update( status.getTaskId, updated )
        }
        case TaskState.TASK_LOST => {
          logger.error( "TASK LOST!!! : " + running.taskId )

          //TODO : should we invalidate the cache on failure??
          killExecutor( running.lambda.lambdaId.get, running.executorId, status.getExecutorId, status.getSlaveId, Some(schedDriver) )

          self ! InvokeLambda( running.lambda, running.event, running.executionId, running.env, Some(running.sender) )
          lambdaMap -= status.getTaskId
        }
      }
      logger.debug( "Output: " + status.getData.toString( "UTF-8" ) )
      logger.debug( "Message: " + status.getMessage )
    }

    //schedDriver.acknowledgeStatusUpdate(status)
  }

  // this is a framework message from our executor, returning the output of a lambda execution
  // Akka-message it back to the sender so they can serve it up, etc.
  override def frameworkMessage(driver: SchedulerDriver, executor: ExecutorID, slave: SlaveID, mesg: Array[Byte]): Unit = {
    logger.debug("Received framework message %s %s %s ".format(executor, slave, ByteString.copyFrom( mesg ).toStringUtf8 ))
  }

  override def resourceOffers(driver: SchedulerDriver, offers: java.util.List[Offer]): Unit = {
    logger.debug( "scheduler actor received offers ..." )
    offerActor ! IncomingOffers( offers.asScala )
  }

  override def offerRescinded(schedulerDriver: SchedulerDriver, offer: OfferID): Unit = {
    // i think this happens when we attempt to launch against an old request
    // i imagine that's bad news; it means we've probably entered into a high-latency scenario
    // let's optimize that if we run into it; in the meantime, it means you need to put the lambda execution into
    // some queue where it can be handled on the next offer
    logger.debug("Offer %s rescinded".format(offer))
  }

  override def registered(driver: SchedulerDriver, frameworkID: FrameworkID, master: MasterInfo): Unit = {
    val host = master.getHostname
    val port = master.getPort
    logger.debug(s"Registered with Mesos master [$host:$port]")
  }

  // this means our specialized
  override def executorLost(driver: SchedulerDriver, executor: ExecutorID, slave: SlaveID, i: Int): Unit = {
    logger.debug(s"Lost executor $executor slave $i")
  }

}

object SchedulerActor {
  def props() : Props = Props( new SchedulerActor )
}

