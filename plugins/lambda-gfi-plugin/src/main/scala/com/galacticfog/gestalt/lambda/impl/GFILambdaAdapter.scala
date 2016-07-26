package com.galacticfog.gestalt.lambda.impl

import java.util.concurrent.TimeoutException

import akka.util.Timeout
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import scala.math._
import akka.actor.ActorSystem
import akka.pattern.ask
import com.galacticfog.gestalt.lambda.impl.actor.GFIMessages.{InvalidateCache, TimeoutLambda, InvokeLambda, ShutdownScheduler}
import com.galacticfog.gestalt.lambda.impl.actor.SchedulerActor
import com.galacticfog.gestalt.lambda.io.domain.{LambdaContentType, LambdaResult, LambdaDao, LambdaEvent}
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import com.galacticfog.gestalt.utils.json.JsonUtils._
import play.api.libs.json.{Json}
import com.newrelic.api.agent.Trace

case class LambdaHeader( key : String, value : String )

//TODO : option sequence of headers, not sure I like that construction
case class GFILambdaInfo( artifactUri : Option[String], code : Option[String], description : Option[String], functionName : String,
                          handler : String, memorySize : Int, cpus : Float, publish : Boolean, role : String, runtime : String,
                          timeoutSecs : Option[Int], headers : Option[Seq[LambdaHeader]], lambdaId : Option[String] = None )

object GFILambdaInfo {
  implicit val lambdaHeaderFormat = Json.format[LambdaHeader]
  implicit val lambdaInfoFormat = Json.format[GFILambdaInfo]
}

class GFILambdaAdapter extends LambdaAdapter {

  val log = LoggerFactory.getLogger( GFILambdaAdapter.this.getClass )

  sys.addShutdownHook( akkaShutdown )
  val actorSystem = ActorSystem("GFILambdaAdapterSystem")
  //TODO : this should be a ROUTER to a cluster of REMOTE schedulers
  val schedulerActor = actorSystem.actorOf( SchedulerActor.props(), "scheduler-actor" )

  def akkaShutdown = {
    log.debug( "Lambda Akka actorsystem ::begin shutdown()" )

    implicit val timeout : Timeout = Timeout.durationToTimeout( 5 seconds )
    schedulerActor ? ShutdownScheduler
    actorSystem.stop( schedulerActor )

    actorSystem.shutdown()
    actorSystem.awaitTermination()
    log.debug( "Lambda Akka actorsystem ::shutdown()" )
  }

  def getPluginName : String  = "GFILambdaAdapter"

  def createLambda( data : LambdaDao ) : String = {
    //Logger.debug("GFILambdaAdapter::createLambda")
    "TODO : finish create"
  }

  def invalidateCache( data : LambdaDao ) : Unit = {
    log.debug( s"Invalidate Cache( ${data.id} )")
    schedulerActor ! InvalidateCache( data )
  }

  def deleteLambda( data : LambdaDao ) : Unit = {
    //Logger.debug("GFILambdaAdapter::deleteLambda")
  }

  @Trace(dispatcher=true)
  def invokeLambda( data : LambdaDao, event : LambdaEvent, env : Future[Map[String,String]], creds : Option[String] )(implicit context : ExecutionContext ) : Future[LambdaResult] = {
    log.debug("GFILambdaAdapter::invokeLambda")

    //val lambdaInfo = data.artifactDescription.validate[GFILambdaInfo] getOrElse ???
    val lambdaInfo = parseAs[GFILambdaInfo]( data.artifactDescription, "Failed to parse Lambda event" ).copy( lambdaId = data.id )

    //TODO : determine the max timeout, it should be environmental config
    implicit val timeoutSeconds: Int = min( lambdaInfo.timeoutSecs.getOrElse( 180 ), 180 )
    implicit val timeout: Timeout = Timeout.durationToTimeout( timeoutSeconds seconds )
    val uuid = java.util.UUID.randomUUID( ).toString

    //search the list of headers for the accept header and get the content type
    val contentType = lambdaInfo.headers.flatMap{
      heads => heads.find{ h => h.key == "Accept" }.headOption
    }.map( head => LambdaContentType( head.value ) ).getOrElse( LambdaContentType.TEXT )

    log.debug( "HEADERS : ")
    lambdaInfo.headers.map( heads => heads.foreach( h => log.debug( "- " + h.key + " -> " + h.value ) ) )
    log.debug( "CONTENT TYPE : " + contentType.name )

    try {

      //TODO : this doesn't scale, return a future

      val fut = schedulerActor ? InvokeLambda( lambdaInfo, event, uuid, env, senderActor = None, creds )
      fut.map( res => new LambdaResult( contentType, res.asInstanceOf[String] ) )


    } catch {
      case ex : TimeoutException => {
        log.debug( s"TIMEOUT : Lambda timed out after $timeoutSeconds")
        schedulerActor ! TimeoutLambda( uuid )
        Future {
          new LambdaResult( LambdaContentType.TEXT, ex.getStackTraceString )
        }
      }
    }
  }
}
