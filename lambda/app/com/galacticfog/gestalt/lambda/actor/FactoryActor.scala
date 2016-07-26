package com.galacticfog.gestalt.lambda.actor

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import com.galacticfog.gestalt.lambda.actor.InvokeActor
import com.galacticfog.gestalt.lambda.actor.LambdaMessages._
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import com.galacticfog.gestalt.lambda.utils.SecureIdGenerator
import play.api.Logger
import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._

class FactoryActor( lambdaAdapter : LambdaAdapter ) extends Actor with ActorLogging {

  val ID_LENGTH = 24
  val actorMap = scala.collection.mutable.Map[String,ActorRef]()
  var envActor : ActorRef = null
  var count = 0

  override def preStart() = {
    val id = SecureIdGenerator.genId62( ID_LENGTH )
    envActor = newEnvironmentActor(count, id)
  }

  def receive = LoggingReceive { handleRequests }

  val handleRequests : Receive = {

    // TODO : to remove, no
    case IncomingEvent( eventName, event, executionId ) => {
      Logger.debug( s"IncomingEvent( $eventName )" )

      val id = SecureIdGenerator.genId62( ID_LENGTH )
      val actor = newLookupActor(count, id)
      count += 1
      actorMap += (id -> actor)

      actor ! LookupLambda( lambdaAdapter, eventName = Some(eventName), lambdaId = None, event, executionId )
    }

    case IncomingInvoke( lambdaId, event, executionId, creds ) => {
      Logger.debug( s"IncomingInvoke( $lambdaId )" )

      val id = SecureIdGenerator.genId62( ID_LENGTH )
      val actor = newLookupActor(count, id)
      count += 1
      actorMap += (id -> actor)

      actor ! LookupLambda( lambdaAdapter, eventName = None, lambdaId = Some(lambdaId), event, executionId, syncActor = None, creds = creds )
    }

    case IncomingInvokeSync( lambdaId, event, executionId, creds ) => {
      Logger.debug( s"IncomingInvokeSync( $lambdaId )" )

      val id = SecureIdGenerator.genId62( ID_LENGTH )
      val actor = newLookupActor(count, id)
      count += 1
      actorMap += (id -> actor)

      Logger.debug( "Adding context.sender actorref with id : " + context.sender.path.toSerializationFormat )

      actor ! LookupLambda( lambdaAdapter, eventName = None, lambdaId = Some(lambdaId), event, executionId, syncActor = Some(context.sender), creds = creds )
    }

    case InvokeLambda( lambdaAdapter, lambda, lambdaEvent, executionId, syncActor, creds ) => {
      Logger.debug( "InvokeLambda" )

      val id = SecureIdGenerator.genId62( ID_LENGTH )
      val actor = newInvokeActor(count, id)
      count += 1
      actorMap += (id -> actor)

      actor ! InvokeLambda( lambdaAdapter, lambda, lambdaEvent, executionId, syncActor, creds )
    }

    case LookupVariables( lambda, env ) => {
      envActor ! LookupVariables( lambda, env )
    }

    case StopActor( id ) => {
      Logger.debug( "StopActor( " + id + " )" )

      val actor = actorMap.get( id ).get
      context.system.stop( actor )
    }

    case LambdaShutdown => {
      //TODO : do we need to propagate this?  Need to read the literature about clean shutdown.  We shutdown at the top
      // but I think we only need to do the trickle down if we're holding resources which we're not
    }
  }

  def newLookupActor( n : Int, id : String ) = {
    Logger.debug( s"newLookupActor(( $n )" )
    context.actorOf( LookupActor.props( id ), name = s"lambda-lookup-$n" )
  }

  def newInvokeActor( n : Int, id : String ) = {
    Logger.debug( s"newInvokeActor(( $n )" )
    context.actorOf( InvokeActor.props( id ), name = s"lambda-invoke-$n" )
  }

  def newEnvironmentActor( n : Int, id : String ) = {
    Logger.debug( s"newEnvironmentActor(( $n )" )
    context.actorOf( EnvironmentActor.props( id ), name = s"environment-actor-$n" )
  }

}


object FactoryActor {
    def props( lambdaAdapter: LambdaAdapter ) : Props = Props( new FactoryActor( lambdaAdapter ) )
}
