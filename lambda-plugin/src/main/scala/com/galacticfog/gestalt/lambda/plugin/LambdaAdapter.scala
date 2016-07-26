package com.galacticfog.gestalt.lambda.plugin

import com.galacticfog.gestalt.lambda.io.domain.{LambdaResult, LambdaEvent, LambdaDao}
import com.galacticfog.gestalt.utils.servicefactory.GestaltPlugin

import scala.concurrent.{ExecutionContext, Future}

trait LambdaAdapter extends GestaltPlugin {

  //the return should be a stringified json payload that will
  //contain the adapter specific data for invoking lambdas
  def createLambda( data : LambdaDao ) : String

  //this will either complete or throw and exception, no need for a return
  def deleteLambda( data : LambdaDao ) : Unit

  def invokeLambda( data : LambdaDao, event : LambdaEvent, env : Future[Map[String,String]], creds : Option[String] = None )(implicit context : ExecutionContext ) : Future[LambdaResult]

  //this means that the environment has changed and the next invocation should repull the environment variables
  def invalidateCache( data : LambdaDao ) : Unit
}
