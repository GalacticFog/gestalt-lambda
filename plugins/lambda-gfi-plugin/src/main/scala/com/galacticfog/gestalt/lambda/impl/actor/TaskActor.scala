package com.galacticfog.gestalt.lambda.impl.actor

import akka.actor.{Props, Actor, ActorLogging}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.lambda.impl.EnvironmentCache
import com.galacticfog.gestalt.lambda.impl.actor.GFIMessages._
import com.google.protobuf.ByteString
import org.apache.mesos.Protos
import org.apache.mesos.Protos._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.libs.json.{Json, JsObject}
import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import com.newrelic.api.agent.Trace
import scala.concurrent.ExecutionContext.Implicits.global


class TaskActor( role : String ) extends Actor with ActorLogging {

  val logger = LoggerFactory.getLogger( getClass )

  val MAX_LAMBDAS = sys.env.getOrElse( "MAX_LAMBDAS_PER_OFFER", "6" ).toInt
  val INITIAL_DELAY_MILLIS = sys.env.getOrElse( "INITIAL_DELAY", "100" ).toInt

  val taskMap : mutable.HashMap[String, Protos.TaskID] = new mutable.HashMap[String, Protos.TaskID]() with mutable.SynchronizedMap[String, Protos.TaskID]
  val lambdaQ : mutable.ListBuffer[QueuedLambda] = new mutable.ListBuffer[QueuedLambda]()
  val envCache = new EnvironmentCache

  def numTasks : Int = { lambdaQ.size }

  @Trace(dispatcher=true)
  def receive = LoggingReceive { handleRequests }

  def handleRequests : Receive = {

    case QueueLambda( data, event, uuid, env, senderActor, creds ) => {
      logger.trace( s"Qeueing lambda (${data.lambdaId})")
      //NOTE : We may have been a queued lambda and thus we should respond to the original sender
      lambdaQ += new QueuedLambda( senderActor, data, event, DateTime.now, uuid, env, creds )

      //this is to allow some tasks to spool up before sending out
      context.system.scheduler.scheduleOnce( INITIAL_DELAY_MILLIS millis, context.parent, RequestOffer )
    }

    case IncomingOffer( offer : Protos.Offer ) => {
      logger.trace( s"IncomingOffer(${offer.getId.getValue})")

      debugOffer( offer )

      //NOTE : we may change this strategy to hoard them for
      //       a short time to improve latency
      if( lambdaQ.isEmpty )
      {
        logger.trace( s"queue is empty declining offer [${offer.getId.getValue}]")
        sender ! RejectOffer( offer )
      }
      else
      {
        val data = new OfferMatch( offer )
        self ! MatchOffer( data )
      }
    }

    case MatchOffer( data : OfferMatch ) => {
      logger.trace( s"matching offer [${data.offer.getId.getValue}]")
      matchOffer( data )
    }
  }

  @Trace(dispatcher=true)
  def matchOffer( data : OfferMatch ) : Unit = {

    val offerCpu = getResourceTotal( data.offer, "cpus" )
    val offerRam = getResourceTotal( data.offer, "mem" )

    val remainingCpu = offerCpu - data.spentCpu
    val remainingRam = offerRam - data.spentRam

    if( data.lambdas.size < MAX_LAMBDAS ) {

      lambdaQ.find { lambda =>
        ( lambda.lambda.cpus < remainingCpu &&
          lambda.lambda.memorySize.toDouble < remainingRam
          )
      } match {
        case Some( l ) => {
          lambdaQ -= l
          val task = getTask( l, data.offer )
          val lambdaTask = new LambdaTask( l, task )
          data.lambdas.enqueue( lambdaTask )

          val newCPU = data.spentCpu + l.lambda.cpus
          val newRAM = data.spentRam + l.lambda.memorySize.toDouble
          val update = data.copy( spentCpu = newCPU, spentRam = newRAM )

          logger.trace( s"\t[ remains[ ${remainingCpu}, ${remainingRam} ] - spent[ ${data.spentCpu}, ${data.spentRam} ] - matching[ ${l.lambda.cpus}, ${l.lambda.memorySize} ]" )

          self ! MatchOffer( update )
        }
        case None => {
          if ( data.lambdas.isEmpty ) {
            //TODO ; is this right?  nothing will fit anyway
            logger.trace( s"lambdas empty rejecting offer [${data.offer.getId.getValue}]" )
            context.parent ! RejectOffer( data.offer )
          }
          else {
            //we've queued some work, but no more fits, so send it
            logger.trace( s"asking to launch tasks (${data.lambdas.size})" )
            context.parent ! LaunchTasks( data )
          }
        }
      }
    }
    else {
      //we've queued some work, but no more fits, so send it
      logger.trace( s"asking to launch tasks (${data.lambdas.size})" )
      context.parent ! LaunchTasks( data )
    }

  }

  @Trace(dispatcher=true)
  def getTask( lambda : QueuedLambda, offer : Protos.Offer ) : TaskInfo = {

    val (containerName, commandString, executorName) = getRuntimeInfo( lambda.lambda.runtime )

    //this is actually an important piece of debug to figure out if you're running the right version of the container
    //logger.debug( "CONTAINER NAME : " + containerName )

    val commandInfoBuilder: CommandInfo.Builder = if ( lambda.lambda.artifactUri.isDefined && lambda.lambda.artifactUri.get.length > 0 ) {

      val artifactUri = CommandInfo.URI.newBuilder
        .setValue( lambda.lambda.artifactUri.get )
        .setExtract( true )
        //TODO : this needs to be configurable (store it with the lambda in the db)
        .setCache( true )
        .build

      CommandInfo.newBuilder( )
        .setValue( commandString )
        .addUris( artifactUri )

    } else {

      CommandInfo.newBuilder( )
        .setValue( commandString )
    }

    val cmdEnv = envCache.getEnvironment( lambda.lambda.lambdaId.get, lambda.env )
    commandInfoBuilder.setEnvironment( cmdEnv )

    val commandInfo = commandInfoBuilder.build

    //TODO : we would either disable force pull or make it an option
    val docker = Protos.ContainerInfo.DockerInfo.newBuilder
      .setImage( containerName )
      .setForcePullImage( false )
      .build

    val container = Protos.ContainerInfo.newBuilder
      .setType( Protos.ContainerInfo.Type.DOCKER )
      .setDocker( docker )
      .build

    val executorInfo: ExecutorInfo = ExecutorInfo.newBuilder( )
      .setExecutorId( ExecutorID.newBuilder( ).setValue( lambda.uuid ) )
      .setSource( "scala" )
      .setContainer( container )
      .setCommand( commandInfo )
      .build

    val taskId: Protos.TaskID = Protos.TaskID.newBuilder( )
      .setValue( lambda.uuid )
      .build

    val dataObject = lambda.event.data.as[JsObject]

    val creds : String = lambda.creds.getOrElse("")
    val codeString: String = lambda.lambda.code.getOrElse( "" )
    val eventData: String = Json.stringify( Json.obj(
      ( "lambdaName" -> lambda.lambda.handler ),
      ( "functionName" -> lambda.lambda.functionName ),
      ( "code" -> codeString ),
      ( "data" -> dataObject.toString ),
      ( "creds" -> creds )
    ) )

    TaskInfo
      .newBuilder( )
      .setName( "task " + taskId.getValue( ) )
      .setTaskId( taskId )
      .setSlaveId( offer.getSlaveId( ) )
      .addResources(
        Resource.newBuilder( )
          .setRole( role )
          .setName( "cpus" )
          .setType( Value.Type.SCALAR )
          .setScalar( Value.Scalar.newBuilder( )
          .setValue( lambda.lambda.cpus ) )
      )
      .addResources(
        Resource.newBuilder( )
          .setRole( role )
          .setName( "mem" )
          .setType( Value.Type.SCALAR )
          .setScalar( Value.Scalar.newBuilder( )
          .setValue( lambda.lambda.memorySize ) )
      )
      .setData( ByteString.copyFromUtf8( eventData ) )
      .setExecutor( ExecutorInfo.newBuilder( executorInfo ) )
      .build

    //log.trace( "launching task..." )
  }

  private[this] def getResourceValue(offer: Offer, name: String): String = {
    offer.getResourcesList.asScala.find(_.getName == name) map {_.getScalar.getValue.toString} getOrElse ""
  }

  private[this] def getResourceTotal(offer: Offer, name: String): Double = {
    offer.getResourcesList.asScala.filter(_.getName == name).foldLeft(0.0)( (tot,res) => tot + res.getScalar.getValue )
  }

  private[this] def getResources(offer: Offer, name: String) : Seq[Protos.Resource] = {
    offer.getResourcesList.asScala.filter(_.getName == name)
  }

  private[this] def getUnreservedResources(offer: Offer, name: String) : Seq[Protos.Resource] = {
    offer.getResourcesList.asScala.filter(_.getName == name).filter( res => !res.getReservation.hasPrincipal )
  }

  def getRuntimeInfo( runtime : String ) = {
    runtime match {
      case "nodejs" => {
        (
          sys.env.getOrElse( "JS_EXECUTOR", "galacticfog.artifactoryonline.com/lambda-javascript-executor:1.2.0-SNAPSHOT-edb2e1c5"),
          "bin/lambda-javascript-executor",
          "JavaScriptExecutor"
          )
      }
      case "java" =>
      {
        (
          sys.env.getOrElse( "JAVA_EXECUTOR", "galacticfog.artifactoryonline.com/lambda-java-executor:1.2.0-SNAPSHOT-edb2e1c5"),
          "bin/lambda-java-executor",
          "JavaExecutor"
          )
      }
      case "scala" =>
      {
        (
          sys.env.getOrElse( "JAVA_EXECUTOR", "galacticfog.artifactoryonline.com/lambda-java-executor:1.2.0-SNAPSHOT-edb2e1c5"),
          "bin/lambda-java-executor",
          "JavaExecutor"
          )
      }
      case "dotnet" | "csharp" => {
        (
          sys.env.getOrElse( "DOTNET_EXECUTOR", "galacticfog.artifactoryonline.com/lambda-dotnet-alt-executor:1.2.0-SNAPSHOT-edb2e1c5"),
          "bin/lambda-dotnet-alt-executor",
          "DotNetExecutor"
          )
      }
      case _ => {
        throw new Exception( "FAILED to match runtime" )
      }
    }
  }

  @Trace(dispatcher=true)
  def debugOffer( offer : Protos.Offer ): Unit = {
    val sb = new StringBuilder
    sb ++= "\nReceived Offer : \n"
    val cpus = getResourceTotal( offer, "cpus" )
    val mem = getResourceTotal( offer, "mem" )
    sb ++= s"\t[ cpu ($cpus), mem ($mem) ]\n"

    logger.trace( sb.toString )
  }
}

object TaskActor {
  def props( role : String ) : Props = {
    Props( new TaskActor( role ) )
  }
}
