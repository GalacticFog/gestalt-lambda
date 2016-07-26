package com.galacticfog.gestalt.lambda.actor

import akka.actor.{Props, Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.galacticfog.gestalt.lambda.actor.LambdaMessages.{LookupVariables, StopActor, InvokeLambda}
import com.galacticfog.gestalt.lambda.io.domain.{LambdaResult, ResultDao}
import com.galacticfog.gestalt.lambda.util.WebClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scala.concurrent.{Await, Future, Promise}

class InvokeActor( id : String ) extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  def getClientConfig : MetaClientConfig = {
    val protocol = sys.env.getOrElse( "META_PROTOCOL", "http" )
    val host = sys.env.getOrElse( "META_HOSTNAME", "meta.dev2.galacticfog.com" )
    val port = sys.env.getOrElse( "META_PORT", "80" ).toInt
    val user = sys.env.getOrElse( "META_USER", "root" )
    val password = sys.env.getOrElse( "META_PASSWORD", "letmein" )

    new MetaClientConfig( protocol, host, port, user, password )
  }

  val handleRequests : Receive = {
    case InvokeLambda( lambdaAdapter, lambda, event, executionId, syncActor, creds ) => {
      log.debug( s"invokeLambda( ${lambda.id} )" )

      if( syncActor.isDefined )
      {
        log.debug( s"InvokeActor received syncPath : " + syncActor.get.path.toSerializationFormat )
      }

      //*************
      // NEW THREAD
      //
      // I tried to do this with actors but promises don't like to be serialized, so I'm doing this in the
      // thread space of this invoke actor.
      //
      //-->

      val promise = Promise[Map[String,String]]()
      Future {

        val builder = new (com.ning.http.client.AsyncHttpClientConfig.Builder)()
        val client = new play.api.libs.ws.ning.NingWSClient(builder.build())
        val config = getClientConfig

        val wc = new WebClient( client, config.protocol, config.host, config.port, config.user, config.password )

        try {

          log.debug( s"Asking meta for env (${config.protocol}, ${config.host}, ${config.port}, ${config.user})")
          val response = wc.get( s"/lambdas/${lambda.id.get}/env" )
          val result = Await.result( response, 3 seconds )
          log.debug( s"Received meta result ${result.toString}" )

          promise.success( result.validate[Map[String,String]].get )

        } catch {
          case ex : Exception => {
            ex.printStackTrace()
            log.debug( "Variables timed out" )
            promise.success( Map[String,String]())
          }
        }
      }

      //TODO : outputting should be either message queue, redis cache, db - adapter
      val fut : Future[LambdaResult] = lambdaAdapter.invokeLambda( lambda, event, promise.future, creds )


      //we need to await the result here
      implicit val timeoutSeconds: Int = 181
      implicit val timeout: Timeout = Timeout.durationToTimeout( timeoutSeconds seconds )
      val result = Await.result( fut, timeoutSeconds seconds  )

      log.debug( "Received adapter result : " + result.result )

      try {
        val dbResult : ResultDao = ResultDao.create(
          lambdaId = lambda.id.get,
          executionId = executionId,
          contentType = result.contentType,
          result = result.result
          //TODO : log result??
        )
      }
      catch {
        case ex : Exception => {
          log.debug( s"ERROR : lambda(id:${lambda.id.get}) deleted, no result persisted.  result : " + result )
        }
      }

      //if there's a waiting response, then send them the result
      syncActor.map{ actor =>
        log.debug( "Sending Sync Actor Response : " + result )
        actor ! result
      }

      log.debug( s"Stopping Invoke Actor ( $id )" )
      context.parent ! StopActor( id )
    }
  }
}

object InvokeActor {
  def props( id : String ) : Props = Props( new InvokeActor( id ) )
}
