package com.galacticfog.lambda.executor

import java.io.{FilenameFilter, FileFilter, File}

import com.galacticfog.gestalt.utils.servicefactory.DynLoader
import com.google.protobuf.ByteString
import org.apache.mesos.Protos._
import org.apache.mesos.{ExecutorDriver, Executor, MesosExecutorDriver}
import play.api.libs.json.{JsObject, Json}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class DotNetExecutor extends Executor {

  override def shutdown(executorDriver: ExecutorDriver): Unit = System.exit(9)

  override def disconnected(executorDriver: ExecutorDriver): Unit = println("LambdaExecutor disconnected")

  override def killTask(executorDriver: ExecutorDriver, taskID: TaskID): Unit = {
    println("killTask[$taskID]")

    executorDriver.sendStatusUpdate(TaskStatus.newBuilder()
      .setTaskId(taskID)
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

        println( "KILLING DOTNET EXECUTOR" )

        driver.abort()
        driver.stop()

      }
      case _ => {
        println( "UNKNOWN FRAMEWORK MESSAGE RECEIVED : " + message )
      }
    }
  }

  override def launchTask(driver: ExecutorDriver, taskInfo: TaskInfo): Unit = {
    println("launching task" )

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
    val lambdaName = ( eventData \ "lambdaName" ).as[String]
    val functionName = ( eventData \ "functionName" ).as[String]
    val lambdaData = ( eventData \ "data" ).as[String]
    val creds = ( eventData \ "creds" ).as[String]

    //this has to be a bit different here because of how we're going to invoke the dotnet lambdas
    val payload = Json.obj( "data" -> lambdaData, "context" -> creds ).toString

    println( "lambdaData : " + lambdaData )

    // We are running in a "sandbox" on the mesos host that is a directory that is mounted to this container
    // at the mount point "/mnt/mesos/sandbox".  Thus, we need to prefix the verticle name below with the
    // absolute path.  On the hose the sandbox location is configurable but by default is in
    // "/tmp/mesos/slaves/{id}/frameworks/{id}/executors/{name}/runs/{id}

    val sandboxDir = "/mnt/mesos/sandbox/"

    //TODO : we should probably abstract this executor framework out and implement flavored workers here

    import scala.sys.process._


    //TODO : currently we're only accepting "fat jar" dotnet packages that have the entire runtime library set necessary bundled with the app
    //val cmd = "cd " + sandboxDir + " && sudo dotnet restore && sudo dotnet run '" + lambdaData + "'"
    val cmd = "cd " + sandboxDir + " && sudo -E " + lambdaName + " '" + payload + "'"

    println( "COMMAND : " + cmd )


    val future = Some( Future {
      Process( Seq( "bash", "-c", cmd ), None, sys.env.toArray: _* ).!!
    } )

    future.get.onComplete { runResult =>

      println( "RESULT : " + runResult.get )

      //driver.sendFrameworkMessage( runResult.get.getBytes )

      driver.sendStatusUpdate( TaskStatus.newBuilder( )
        .setTaskId( taskInfo.getTaskId )
        .setState( TaskState.TASK_FINISHED )
        .setMessage( runResult.get )
        .build( )
      )
    }

    future.get.onFailure {
      case e: Exception => {
        e.printStackTrace
        println( "Lamba Execution failed with message : " + e.getMessage )

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

object DotNetExecutor extends App {
  println("Starting LambdaExecutor - 2")
  val driver = new MesosExecutorDriver(new DotNetExecutor)
  val status = driver.run()
  println("Driver Status after run : " + status)
  System.exit(if (status == Status.DRIVER_STOPPED) 0 else 1)
}
