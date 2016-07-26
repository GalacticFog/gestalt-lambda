package com.galacticfog.gestalt.lambda.test

import akka.util.Timeout
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSAuthScheme

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.Source

object TestHarnessApp {

  val log = LoggerFactory.getLogger( this.getClass )

  val RUN_SECONDS = sys.env.getOrElse( "TEST_RUN_SECONDS", "60" ).toInt
  val TESTS_PER_SECOND = sys.env.getOrElse( "TESTS_PER_SECOND", "1" ).toDouble
  val HOST = sys.env.getOrElse( "TEST_HOST", "localhost" )
  val PORT = sys.env.get( "TEST_PORT" )
  val PATH = sys.env.getOrElse( "TEST_PATH", "/lambdas" )
  val PAYLOAD = sys.env.get( "TEST_PAYLOAD" )
  val TEST_TOKEN = sys.env.getOrElse( "TEST_TOKEN", "letmein" )


  def main( args : Array[String] ) : Unit = {

    //convert to nanoseconds
    val testsPerNano = TESTS_PER_SECOND.toDouble / 1e9
    val testPeriod = 1.0 / testsPerNano

    log.debug( "Starting test harness for " + RUN_SECONDS + " seconds..." )
    log.debug( "Test period is " + testPeriod + " nanosecods" )

    val start = System.nanoTime()

    var lastTestTime = System.nanoTime
    do {

      if( (System.nanoTime - lastTestTime) < testPeriod )
      {
        val waitNanos = testPeriod - (System.nanoTime - lastTestTime)
        val waitMillis = waitNanos / 1e6
        //log.debug( s"Waiting $waitMillis for next test ..." )

        Thread.sleep( waitMillis.toInt )
      }

      //log.debug( "Launching test..." )
      launch()
      lastTestTime = System.nanoTime

    } while( (start + (RUN_SECONDS * 1e9) > System.nanoTime ) )

    log.debug( "Stopped test harness" )
  }

  def launch(): Unit = {

    val builder = new ( com.ning.http.client.AsyncHttpClientConfig.Builder )( )
    val client = new play.api.libs.ws.ning.NingWSClient( builder.build( ) )

    val portString = if( PORT.isDefined ) s":${PORT.get}" else ""
    val uri =  s"http://${HOST}$portString$PATH"
    //log.debug( "calling : " + uri )

    val start = System.nanoTime()

    Future {

      val res = if( PAYLOAD.isDefined ) {
        val load = Source.fromFile("./" + PAYLOAD.get).getLines.toList.mkString
        client.url( uri ).withTimeout( 60000 ).withHeaders( ( "Authorization" -> s"Bearer ${TEST_TOKEN}" ) ).withHeaders(("Content-type" -> "application/json")).post(load)
      }
      else
      {
        client.url( uri ).withTimeout( 60000 ).withHeaders( ( "Authorization" -> s"Bearer ${TEST_TOKEN}" ) ).get
      }
      val result = Await.result( res, 120 seconds )
      result
    }.onComplete{ resp =>

      val sb = new StringBuilder
      sb ++= ( "test result : \n")
      sb ++= (resp.get.body + "\n")
      resp.get.allHeaders.foreach{ head =>
        sb ++= (head._1 + "\n\t")
        head._2.foreach{ entry =>
          sb ++= (entry + ", ")
        }
        sb ++= "\n"
      }
      log.debug( sb.toString )

      client.close

      val end = System.nanoTime
      val secondsToComplete = (end - start) * 1e-9
      log.info("elapsed time : " + secondsToComplete )
    }

  }
}
