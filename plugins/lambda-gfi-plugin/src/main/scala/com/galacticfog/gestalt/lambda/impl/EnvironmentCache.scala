package com.galacticfog.gestalt.lambda.impl

import java.util.concurrent.TimeoutException

import org.apache.mesos.Protos
import org.joda.time.DateTime
import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

case class EnvironmentCacheEntry( lambdaId : String, env : Protos.Environment, queuedTime : DateTime = DateTime.now )
class EnvironmentCache {

  val cache : mutable.Map[String, EnvironmentCacheEntry] = mutable.Map[String, EnvironmentCacheEntry]()
  val EXPIRATION_SECONDS = sys.env.getOrElse( "ENV_CACHE_EXPIRATION_SECONDS", "900" ).toInt

  def getEnvironment( lambdaId : String, env : Future[Map[String,String]] ) : Protos.Environment = {

    val cacheEntry = cache.get( lambdaId )

    if( !cacheEntry.isDefined || cacheEntry.get.queuedTime.plusSeconds( EXPIRATION_SECONDS ).isBeforeNow ) {
      //wait for the future
      try {
        val result = Await.result( env, 5 seconds )
        val builder = Protos.Environment.newBuilder
        result.foreach{ entry =>
          builder.addVariables( Protos.Environment.Variable.newBuilder
            .setName( entry._1 )
            .setValue( entry._2 )
          )
        }

        val newEnv =  builder.build
        cache( lambdaId ) = new EnvironmentCacheEntry( lambdaId, newEnv )
        newEnv
      }
      catch {
        case ex : TimeoutException => {
          println( "TIMEOUT" )
          Protos.Environment.newBuilder.build
        }
      }
    }
    else {
      cache( lambdaId ).env
    }
  }
}
