package com.galacticfog.gestalt.lambda

import java.util.UUID
import java.util.concurrent.TimeoutException

import akka.actor.{UnhandledMessage, ActorSystem}
import akka.pattern.gracefulStop
import akka.pattern.ask
import akka.util.Timeout
import com.galacticfog.gestalt.lambda.actor.LambdaMessages._
import com.galacticfog.gestalt.lambda.actor.{UnhandledMessageActor, FactoryActor}
import com.galacticfog.gestalt.lambda.io.domain._
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import com.galacticfog.gestalt.utils.servicefactory.ServiceFactory
import com.galacticfog.gestalt.utils.json.JsonUtils._
import play.api.libs.json.{JsSuccess, JsError, Json, JsValue}
import play.api.{Logger => log}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

class LambdaFramework {

  def createLambda( body : JsValue ) : Try[JsValue] = Try {
    log.debug( "createLambda" )

    val inLambda = body.validate[LambdaDao] match {
      case JsSuccess( s, _ ) => s
      case err@JsError(_) => {
        throw new Exception( "Error parsing lambda info : " + Json.toJson( JsError.toFlatJson(err) ) )
      }
    }

    //if they gave us an ID make sure it's a UUID
    if( inLambda.id.isDefined )
    {
      UUID.fromString( inLambda.id.get )
    }

    //TODO : this should be async with a task and an actor (fix when tasks are back)

    val payload = LambdaFramework.lambdaAdapter.createLambda( inLambda )

    val lambda = inLambda.copy( payload = Some(payload) ).create

    Json.toJson( lambda )
  }

  def getLambda( id : String ) : Try[JsValue] = Try {
    log.debug( s"getLambda($id)" )

    LambdaDao.findById( id ) match {
      case Some(s) => {
        Json.toJson( s )
      }
      case None => {
        throw new Exception( s"Lambda not found with id : $id" )
      }
    }
  }

  def updateLambda( id : String, body : JsValue ) : Try[JsValue] = Try {
    log.debug( s"updateLambda($id)")

    val lambda = parseAs[LambdaDao]( body, "Unable to parse lambda body" )
    Json.toJson( lambda.update )
  }

  def searchLambdas( query : Map[String, Seq[String]] ) : Try[JsValue] = Try{
    log.debug( s"searchLambdas" )
    Json.toJson( LambdaDao.findAll )
  }

  def invalidateCache( id : String ) : Try[JsValue] = Try {
    log.debug( s"invalidateCache" )

    val lambda = LambdaDao.findById( id ).getOrElse( throw new Exception( s"Lambda not found with id : $id" ) )

    LambdaFramework.lambdaAdapter.invalidateCache( lambda )
    Json.obj( "status" -> "success" )
  }

  def invokeLambda( lambdaId : String, body : JsValue, creds : Option[String] ) : Try[JsValue] = Try {
    log.debug( s"invokeLambda" )

    //log the thing immediately in the database
    val event = body.validate[LambdaEvent] match {
      case JsSuccess( s, _ ) => s
      case err@JsError(_) => {
        throw new Exception( "Error parsing lambda event : " + Json.toJson( JsError.toFlatJson(err) ) )
      }
    }

    //TODO : we need to return a way to identify this result when we have a result
    //TODO : result table


    val id : String = UUID.randomUUID.toString
    LambdaFramework.lambdaFactory ! IncomingInvoke( lambdaId, event, id, creds )

    Json.obj(
      "id" -> id
    )
  }

  def invokeLambdaSync( lambdaId : String, body : JsValue, creds : Option[String] ) : Try[LambdaResult] = Try{
    log.debug( s"invokeLambdaSync" )

    //log the thing immediately in the database
    val event = body.validate[LambdaEvent] match {
      case JsSuccess( s, _ ) => s
      case err@JsError(_) => {
        throw new Exception( "Error parsing lambda event : " + Json.toJson( JsError.toFlatJson(err) ) )
      }
    }

    //TODO : we need to return a way to identify this result when we have a result
    //TODO : result table

    val id : String = UUID.randomUUID.toString
    implicit val timeout = Timeout(500.seconds)
    val fut = LambdaFramework.lambdaFactory.ask( IncomingInvokeSync( lambdaId, event, id, creds ) )

    //TODO : fix this timeout duration
    try {
      val result = Await.result( fut, 500 seconds ).asInstanceOf[LambdaResult]
      result
    } catch {
      case ex : TimeoutException => {
        ex.printStackTrace
        new LambdaResult( LambdaContentType.TEXT, ex.getStackTraceString )
      }
    }
  }

  def deleteLambda( id : String ) : Try[JsValue] = Try {
    log.debug( s"deleteLambda( $id )" )

    //TODO : this should be async with a task and an actor (fix when tasks are back)

    val lambda = LambdaDao.findById( id ) match {
      case Some(s) => s
      case None => {
        throw new Exception( s"No lambda found with id : $id" )
      }
    }

    LambdaFramework.lambdaAdapter.deleteLambda( lambda )
    LambdaDao.delete( lambda.id.get )

    Json.obj()
  }

  def getResult( id : String ) : Try[JsValue] = Try {
    log.debug( s"getResult( $id )")

    ResultDao.find( id ) match {
      case Some(s) => {
        Json.toJson( s )
      }
      case None => {
        throw new Exception( s"lambda result not found for execution ID : $id" )
      }
    }
  }
}

object LambdaFramework {

  private val ID_LENGTH = 24
  private val lambdaAdapter = init()

  val system = ActorSystem("LambdaActorSystem")
  val lambdaFactory = system.actorOf( FactoryActor.props( lambdaAdapter ), "factory-actor" )

  val unhandledActor = system.actorOf( UnhandledMessageActor.props(), "unhandled-message-actor" )
  system.eventStream.subscribe(unhandledActor, classOf[UnhandledMessage])

  def init() : LambdaAdapter = {
    //TODO : make config
    val adapterName = "GFILambdaAdapter"
    //val adapterName = "AWSLambdaAdapter"
    //val adapterName = "VertxLambdaAdapter"
    ServiceFactory.loadPlugin[LambdaAdapter]( "plugins", adapterName, this.getClass.getClassLoader )
    //new FakeLambdaAdapter
  }

  def getFramework() : LambdaFramework = {
    new LambdaFramework
  }

  def shutdown() : Unit = {

    lambdaFactory ! LambdaShutdown

    try {
      val stopped : Future[Boolean] = gracefulStop(lambdaFactory, scala.concurrent.duration.Duration(5, "seconds"))
      Await.result(stopped, scala.concurrent.duration.Duration(6, "seconds"))

      val unhandledStopped : Future[Boolean] = gracefulStop(unhandledActor, scala.concurrent.duration.Duration(5, "seconds"))
      Await.result(unhandledStopped, scala.concurrent.duration.Duration(6, "seconds"))
      // the actor has been stopped
    }
    catch {
      case e : akka.pattern.AskTimeoutException => throw new Exception( "Akka failed to shutdown cleanly" )
    }

  }

}
