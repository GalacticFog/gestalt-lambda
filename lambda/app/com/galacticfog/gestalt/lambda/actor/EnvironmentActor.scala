package com.galacticfog.gestalt.lambda.actor

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.lambda.io.domain.LambdaDao
import com.galacticfog.gestalt.lambda.actor.LambdaMessages._
import com.galacticfog.gestalt.lambda.util.WebClient
import com.galacticfog.gestalt.lambda.utils.SecureIdGenerator
import play.api.Logger

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

class EnvironmentActor( id : String ) extends Actor with ActorLogging {

  val ID_LENGTH = 24
  val actorMap = scala.collection.mutable.Map[String,ActorRef]()
  var count = 0

  def receive = LoggingReceive { handleRequests }

  val handleRequests : Receive = {

    case LookupVariables( lambda, promise ) => {
      Logger.debug( s"LookupVariables( ${lambda.id} )" )

      val id = SecureIdGenerator.genId62( ID_LENGTH )
      val actor = newVariableFetchActor(count, id)
      count += 1
      actorMap += (id -> actor)

      actor ! LookupVariables( lambda, promise )
    }

    case StopActor( id ) => {
      Logger.debug( s"StopActor( ${id} )" )
      val actor = actorMap.get( id ).get
      context.system.stop( actor )
      actorMap -= id
    }

  }

  def newVariableFetchActor( n : Int, id : String ) = {
    Logger.debug( s"newVariableFetchActor(( $n )" )
    context.actorOf( VariableFetchActor.props( id ), name = s"variable-fetch-$n" )
  }
}

object EnvironmentActor {
  def props( id : String ) : Props = Props( new LookupActor( id ) )
}

case class MetaClientConfig( protocol : String, host : String, port : Int, user : String, password : String )

class VariableFetchActor( id : String ) extends Actor with ActorLogging {

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

    case LookupVariables( lambda, env ) => {
      Logger.debug( s"LookupVariables( ${lambda.id} )" )

      val promise = env.asInstanceOf[Promise[Map[String,String]]]

      val builder = new (com.ning.http.client.AsyncHttpClientConfig.Builder)()
      val client = new play.api.libs.ws.ning.NingWSClient(builder.build())
      val config = getClientConfig

      val wc = new WebClient( client, config.protocol, config.host, config.port, config.user, config.password )

      try {

        val response = wc.get( s"/lambdas/${lambda.id.get}/env" )
        val result = Await.result( response, 3 seconds ).validate[Map[String,String]].get

        promise.success( result )

      } catch {
        case ex : Exception => {
          ex.printStackTrace()
          log.debug( "Variables timed out" )
          promise.success( Map[String,String]())
        }
      }

      context.parent ! StopActor( id )
    }
  }
}

object VariableFetchActor {
  def props( id : String ) : Props = Props( new VariableFetchActor( id ) )
}
