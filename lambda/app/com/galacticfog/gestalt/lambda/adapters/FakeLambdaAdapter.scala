package com.galacticfog.gestalt.lambda.adapters

import com.galacticfog.gestalt.lambda.io.domain.{LambdaContentType, LambdaResult, LambdaEvent, LambdaDao}
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

class FakeLambdaAdapter extends LambdaAdapter {

  def getPluginName : String = "FakeLambdaAdapter"

  def createLambda( data : LambdaDao ) : String = {
    Logger.debug("FakeLambdaAdapter::createLambda")
    "returned_payload"
  }

  def deleteLambda( data : LambdaDao ) : Unit = {
    Logger.debug("FakeLambdaAdapter::deleteLambda")
  }

  def invalidateCache( data : LambdaDao ) : Unit = {
    Logger.debug( "FakeLambdaAdapter::invalidateCache" )
  }

  def invokeLambda( data : LambdaDao, event : LambdaEvent, env : Future[Map[String,String]], creds : Option[String] )(implicit context : ExecutionContext ) : Future[LambdaResult] = {
    Logger.debug("FakeLambdaAdapter::invokeLambda")
    Future {
      new LambdaResult( LambdaContentType.TEXT, "Done" )
    }
  }

}
