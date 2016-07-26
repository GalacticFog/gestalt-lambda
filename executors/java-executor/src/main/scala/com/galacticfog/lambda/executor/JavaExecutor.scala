package com.galacticfog.lambda.executor

import java.io.{FilenameFilter, FileFilter, File}

import com.galacticfog.gestalt.utils.servicefactory.DynLoader
import com.google.protobuf.ByteString
import org.apache.mesos.Protos._
import org.apache.mesos.{ExecutorDriver, Executor, MesosExecutorDriver}
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.Try

class JavaExecutor extends Executor {

  override def shutdown(executorDriver: ExecutorDriver): Unit = System.exit(9)

  override def disconnected(executorDriver: ExecutorDriver): Unit = println("LambdaExecutor disconnected")

  override def killTask(executorDriver: ExecutorDriver, taskID: TaskID): Unit = {
    println("killTask[$taskID]")

    executorDriver.sendStatusUpdate(TaskStatus.newBuilder()
      .setTaskId( taskID )
      .setState(TaskState.TASK_KILLED)
      .build()
    )

    executorDriver.abort()
    executorDriver.stop()
  }

  override def reregistered(executorDriver: ExecutorDriver, slaveInfo: SlaveInfo): Unit = {
    println("LambdaExecutor reregistered")
  }

  override def registered(executorDriver: ExecutorDriver,
                          executorInfo: ExecutorInfo,
                          frameworkInfo: FrameworkInfo,
                          slaveInfo: SlaveInfo): Unit = {
    println("LambdaExecutor registered")
  }

  override def error(executorDriver: ExecutorDriver, s: String): Unit = {
    println(s"Error: $s")
  }

  override def frameworkMessage(driver: ExecutorDriver, bytes: Array[Byte]): Unit = {
    // framework message is how the executor gets addition launch requests; the bytes will be serialized representation of the GestaltLambdaExecution class, wtf that is
    // also, frameworkMessage is how the executor is told to shutdown, assuming that the scheduler tells it to
    val message = new String( bytes )
    message match {
      case "KILL" => {

        println( "KILLING JAVA EXECUTOR" )

        driver.abort()
        driver.stop()

      }
      case _ => {
        println( "UNKNOWN FRAMEWORK MESSAGE RECEIVED : " + message )
      }
    }
  }

  override def launchTask(driver: ExecutorDriver, taskInfo: TaskInfo): Unit = {
    println("launching task")

    driver.sendStatusUpdate(TaskStatus.newBuilder()
      .setTaskId(taskInfo.getTaskId)
      .setMessage( taskInfo.getData.toStringUtf8 )
      .setState(TaskState.TASK_RUNNING)
      .build()
    )

    //@debug
    println( "Executor::taskData : " + taskInfo.getData.toStringUtf8 )

    //TODO : this should be a clearly defined data structure that is validated when we settle on a format

    val eventData = Json.parse( taskInfo.getData.toStringUtf8 )
    val verticleName = ( eventData \ "lambdaName" ).as[String]
    val functionName = ( eventData \ "functionName" ).as[String]
    val lambdaData = ( eventData \ "data" ).as[String]
    val context = ( eventData \ "creds" ).as[String]

    // We are running in a "sandbox" on the mesos host that is a directory that is mounted to this container
    // at the mount point "/mnt/mesos/sandbox".  Thus, we need to prefix the verticle name below with the
    // absolute path.  On the hose the sandbox location is configurable but by default is in
    // "/tmp/mesos/slaves/{id}/frameworks/{id}/executors/{name}/runs/{id}

    val sandboxDir = "/mnt/mesos/sandbox/"

    //TODO : we should probably abstract this executor framework out and implement flavored workers here

    //NOTE - we're going to asssume that the file exists right in our work dir, and that any jar files found
    // in the work dir are meant to be dynamically loaded

    val workDir = new File( sandboxDir )
    val jars = workDir.listFiles( new FilenameFilter() {
      def accept( dir : File, name : String ) : Boolean = {
        name.endsWith( ".jar" )
      }
    })

    jars.foreach( j => DynLoader.extendClasspath( j, getClass.getClassLoader ) )
    val clazz = getClass.getClassLoader.loadClass( verticleName )
    val instance = clazz.newInstance

    val params = Array( classOf[String], classOf[String] )
    val method = clazz.getDeclaredMethod( functionName, params:_* )

    //TODO : figure out wtf to do with context
    val realParams = Array( lambdaData, context )

    // We launch this into a separate thread execution because we need to be able to respond
    // to the framework messages that we might receive, e.g. "killTask" or "shutdown"
    // TODO : if we're binding we probably need to fix up classpaths

    val future = Future {
      method.invoke( instance, realParams: _* ).asInstanceOf[String]
    }

    future.onComplete{ retVal =>

      println( "RETURN VAL : " + retVal.get )

      // Send the result back to the framework in the form of a FrameworkMessage
      // - assumed to be stringified JSON data
      //driver.sendFrameworkMessage( retVal.get.getBytes )

      driver.sendStatusUpdate(TaskStatus.newBuilder()
        .setTaskId(taskInfo.getTaskId)
        .setState(TaskState.TASK_FINISHED)
        .setMessage( retVal.get )
        .build()
      )
    }

    future.onFailure{
      case e : Exception => {
        e.printStackTrace
        println( "Lamba Execution failed with message : " + e.getMessage )

        driver.sendStatusUpdate(TaskStatus.newBuilder()
          .setTaskId(taskInfo.getTaskId)
          .setState(TaskState.TASK_FAILED)
          .setMessage( e.getMessage + "\n" + e.getStackTraceString )
          .build()
        )
      }
    }
  }
}

object JavaExecutor extends App {
  println("Starting LambdaExecutor - 2")
  val driver = new MesosExecutorDriver(new JavaExecutor)
  val status = driver.run()
  println("Driver Status after run : " + status)
  System.exit(if (status == Status.DRIVER_STOPPED) 0 else 1)
}
