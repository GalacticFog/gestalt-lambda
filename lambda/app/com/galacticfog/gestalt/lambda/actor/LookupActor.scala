package com.galacticfog.gestalt.lambda.actor

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.lambda.io.domain.LambdaDao
import com.galacticfog.gestalt.lambda.actor.LambdaMessages._
import play.api.Logger

class LookupActor( id : String ) extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  val handleRequests : Receive = {

    case LookupLambda( lambdaAdapter, eventName, lambdaId, event, executionId, syncActor, creds ) => {
      Logger.debug( s"LookupLambda( $eventName )" )

      //TODO : this should be offloaded to the actor system
      val optionLambda = if( lambdaId.isDefined ) LambdaDao.findById( lambdaId.get ) else LambdaDao.find( eventName.get )
      optionLambda match {
        case Some(s) => {

          if( syncActor.isDefined )
          {
            Logger.debug( s"LookupLambda passing syncActor to Invoke : " + syncActor.get.path.toSerializationFormat )
          }

          //TODO : this should only forward path if defined
          context.parent ! InvokeLambda( lambdaAdapter, s, event, executionId, syncActor = syncActor, creds )
        }
        case None => {
          log.debug( s"no lambda handler found for eventname : ${eventName}" )
        }
      }

      Logger.debug( s"Stopping LookupActor( $id )" )
      context.parent ! StopActor( id )
    }
  }
}

object LookupActor {
  def props( id : String ) : Props = Props( new LookupActor( id ) )
}
