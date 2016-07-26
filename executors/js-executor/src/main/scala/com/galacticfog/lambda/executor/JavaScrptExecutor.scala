package com.galacticfog.lambda.executor

import org.apache.mesos.Protos._
import org.apache.mesos.{MesosExecutorDriver, ExecutorDriver, Executor}
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class JavaScriptExecutor extends Executor {

  val log = LoggerFactory.getLogger( JavaScriptExecutor.getClass )

  override def shutdown(executorDriver: ExecutorDriver): Unit = System.exit(9)

  override def disconnected(executorDriver: ExecutorDriver): Unit = log.debug("LambdaExecutor disconnected")

  override def killTask(executorDriver: ExecutorDriver, taskID: TaskID): Unit = {
    log.debug("killTask[$taskID]")

    executorDriver.sendStatusUpdate(TaskStatus.newBuilder()
      .setTaskId( taskID )
      .setState(TaskState.TASK_KILLED)
      .build()
    )

    executorDriver.abort()
    executorDriver.stop()
  }

  override def reregistered(executorDriver: ExecutorDriver, slaveInfo: SlaveInfo): Unit = {
    log.debug("LambdaExecutor reregistered")
  }

  override def registered(executorDriver: ExecutorDriver,
                          executorInfo: ExecutorInfo,
                          frameworkInfo: FrameworkInfo,
                          slaveInfo: SlaveInfo): Unit = {
    log.debug("LambdaExecutor registered")
  }

  override def error(executorDriver: ExecutorDriver, s: String): Unit = {
    log.debug(s"Error: $s")
  }

  override def frameworkMessage(driver: ExecutorDriver, bytes: Array[Byte]): Unit = {
    // framework message is how the executor gets addition launch requests; the bytes will be serialized representation of the GestaltLambdaExecution class, wtf that is
    // also, frameworkMessage is how the executor is told to shutdown, assuming that the scheduler tells it to

    val message = new String( bytes )
    message match {
      case "KILL" => {

        log.debug( "KILLING JS EXECUTOR" )

        driver.abort()
        driver.stop()

      }
      case _ => {
        log.error( "UNKNOWN FRAMEWORK MESSAGE RECEIVED : " + message )
      }
    }


  }

  override def launchTask(driver: ExecutorDriver, taskInfo: TaskInfo): Unit = {
    log.debug( "launching task" )

    driver.sendStatusUpdate( TaskStatus.newBuilder( )
      .setTaskId( taskInfo.getTaskId )
      .setMessage( taskInfo.getData.toStringUtf8 )
      .setState( TaskState.TASK_RUNNING )
      .build( )
    )

    //@debug
    log.debug( "Executor::taskData : " + taskInfo.getData.toStringUtf8 )

    //TODO : this should be a clearly defined data structure that is validated when we settle on a format
    // - it should also be factored into a library that can be versioned

    val eventData     = Json.parse( taskInfo.getData.toStringUtf8 )
    val inHandlerName = ( eventData \ "lambdaName" ).as[String]
    val functionName  = ( eventData \ "functionName" ).as[String]
    val code          = ( eventData \ "code" ).as[String]
    val lambdaData    = ( eventData \ "data" ).as[String]
    val creds         = ( eventData \ "creds" ).as[String]

    log.debug( "inHandlerName : " + inHandlerName )
    val handlerName = if( inHandlerName.endsWith(".js") == false ) (inHandlerName + ".js") else inHandlerName
    log.debug( "handlerName : " + handlerName )

    // We are running in a "sandbox" on the mesos host that is a directory that is mounted to this container
    // at the mount point "/mnt/mesos/sandbox".  Thus, we need to prefix the verticle name below with the
    // absolute path.  On the hose the sandbox location is configurable but by default is in
    // "/tmp/mesos/slaves/{id}/frameworks/{id}/executors/{name}/runs/{id}

    val sandboxDir = "/mnt/mesos/sandbox/"

    //!!!IMPORTANT!!! - if we don't set the thread context loader then the nashorn engine
    //                  will not have any of the classpaths that we need

    Thread.currentThread.setContextClassLoader( getClass.getClassLoader )

    //TODO : we should probably abstract this executor framework out and implement flavored workers here

    val worker: JSWorker = new JSWorker(
      verticleName = sandboxDir + handlerName,
      startFunctionName = functionName
    )

    val future = Future {
      log.debug( "launchTask::worker.init() " )
      worker.init()
      log.debug( "launchTask::worker.run() " )
      worker.run( lambdaData, code, creds )
    }

    future.onComplete { retVal =>

      //@debug
      log.debug( "Verticle Return : " + retVal.get )

      // Send the result back to the framework in the form of a FrameworkMessage
      // - assumed to be stringified JSON data

      driver.sendFrameworkMessage( retVal.get.getBytes )

      driver.sendStatusUpdate( TaskStatus.newBuilder( )
        .setTaskId( taskInfo.getTaskId )
        .setState( TaskState.TASK_FINISHED )
        .setMessage( retVal.get )
        .build( )
      )
    }

    future.onFailure {
      case e: Exception => {
        e.printStackTrace
        log.debug( "Lamba Execution failed with message : " + e.getMessage )

        driver.sendStatusUpdate( TaskStatus.newBuilder( )
          .setTaskId( taskInfo.getTaskId )
          .setState( TaskState.TASK_FAILED )
          .setMessage( e.getMessage + "\n" + e.getStackTraceString )
          .build( )
        )
      }
    }
  }
}

object JavaScriptExecutor extends App {

  val log = LoggerFactory.getLogger( JavaScriptExecutor.getClass )

  log.debug("Starting LambdaExecutor - 2")
  val driver = new MesosExecutorDriver(new JavaScriptExecutor)
  val status = driver.run()
  log.debug("Driver Status after run : " + status)
  System.exit(if (status == Status.DRIVER_STOPPED) 0 else 1)
}
