package com.galacticfog.gestalt.lambda.actor

import akka.actor.{ActorPath, ActorRef}
import com.galacticfog.gestalt.lambda.io.domain.{LambdaEvent, LambdaDao}
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import scala.concurrent.Promise
import play.api.libs.json.JsValue

import scala.concurrent.{Future, Promise}

object LambdaMessages {

  sealed trait LambdaMessage

  case class IncomingEvent( eventFilter : String, event : LambdaEvent, executionId : String ) extends LambdaMessage
  case class IncomingInvoke( lambdaId : String, event : LambdaEvent, executionId : String, creds : Option[String] ) extends LambdaMessage
  case class IncomingInvokeSync( lambdaId : String, event : LambdaEvent, executionId : String, creds : Option[String] ) extends LambdaMessage
  case class LookupLambda( lambdaAdapter : LambdaAdapter, eventName : Option[String], lambdaId : Option[String], event : LambdaEvent, executionId : String, syncActor : Option[ActorRef] = None, creds : Option[String] = None ) extends LambdaMessage
  case class LookupVariables( lambda : LambdaDao, env : AnyRef ) extends LambdaMessage
  case class InvokeLambda( lambdaAdapter : LambdaAdapter, lambda : LambdaDao, event : LambdaEvent, executionId : String, syncActor : Option[ActorRef] = None, creds : Option[String] = None ) extends LambdaMessage
  case class StopActor( id : String ) extends LambdaMessage

  case object LambdaShutdown extends LambdaMessage

}
