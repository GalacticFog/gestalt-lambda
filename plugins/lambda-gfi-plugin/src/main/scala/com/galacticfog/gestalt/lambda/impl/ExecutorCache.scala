package com.galacticfog.gestalt.lambda.impl

import java.util.UUID

import org.apache.mesos.{SchedulerDriver, Protos}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class CacheStatus(
  executorId : String,
  lastRan : DateTime = DateTime.now,
  taskId : Option[Protos.TaskID] = None,
  bDone : Boolean = false,
  bRemove : Boolean = false,
  driver : Option[SchedulerDriver] = None,
  executor : Option[Protos.ExecutorID] = None,
  slaveId : Option[Protos.SlaveID] = None,
  environment : Option[Protos.Environment] = None
)

class ExecutorCache {

  val log = LoggerFactory.getLogger( ExecutorCache.this.getClass )

  val cache = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, CacheStatus]]()

  def debugCache() : Unit = {

    val buf = new StringBuilder
    cache.foreach { top =>
      buf ++= ( "\ncache [" + top._1 + "]: \n" )
      top._2.foreach { entry =>
        buf ++= ( "\tentry [" + entry._1 + "]: bDone [" + entry._2.bDone + "], lastRan [" + entry._2.lastRan.toLocalDateTime.toString + "]\n" )
      }
    }

    log.debug( buf.toString )
  }

  def remove( lambdaId : String, lambdaExecId : String ) : Unit = {
    val entryMap = cache.get( lambdaId ) getOrElse ???
    entryMap -= lambdaExecId

    if( entryMap.size == 0 )
    {
      cache -= lambdaId
    }
  }

  //TODO : validate that this actually does the thing
  def invalidateCache( lambdaId : String ) : Unit = {
    val entry = cache.get( lambdaId )
    if( entry.isDefined )
    {
      val keySet = entry.get.keys
      keySet.foreach{ key =>
        entry.get(key) = entry.get(key).copy( bRemove = true )
      }
    }
  }

  def filterExpired( seconds : Int ) : scala.collection.mutable.Map[ String, scala.collection.mutable.Map[String, CacheStatus]] = {

    cache.map{ entry => {
      val oldies = entry._2.filter{ item =>
        (item._2.bRemove ||
        (item._2.bDone && item._2.lastRan.plusSeconds( seconds ).isBeforeNow))
      }
      ( entry._1, oldies )
      }
    }
  }

  def getAvailableExecutor( lambdaId : String ) : Option[(String, CacheStatus)] = {
    cache.get( lambdaId ).flatMap{ entry =>
      entry.find( item => item._2.bDone && !item._2.bRemove )
    }
  }

  def getOrCreateCacheStatus( lambdaId : String, uuid : String ) : CacheStatus = {

    val available = getAvailableExecutor( lambdaId )
    if( !available.isDefined )
    {
      if( cache.contains( lambdaId ) )
        cache( lambdaId ) ++= scala.collection.mutable.Map( uuid -> new CacheStatus( uuid ))
      else
        cache( lambdaId ) = scala.collection.mutable.Map( uuid -> new CacheStatus( uuid ))
      get( lambdaId, uuid ).get
    }
    else {
      log.debug( "found available cache entry : " + available.get._1 )
      available.get._2
    }
  }

  def get( lambdaId : String, uuid : String ) : Option[CacheStatus] = {
    cache.get( lambdaId ).flatMap( _.get( uuid ) )
  }

  /*
    The scala compiler is nice and allows us to create this update method that will allow syntax
    like this :

    val executionCache : ExecutionCache = ...
    val status : CacheStatus = ...

    executionCache( lambdaId, uuid ) = status

    which makes this looke like a regular map update statement, which is nice.

   */

  def update( lambdaId : String, uuid : String, status : CacheStatus ) = {
    if( !cache.contains( lambdaId ) ) ???
    cache.get( lambdaId ).get( uuid ) = status
  }

}
