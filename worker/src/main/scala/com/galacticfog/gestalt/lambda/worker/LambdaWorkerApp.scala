package com.galacticfog.gestalt.lambda.worker

import com.galacticfog.gestalt.lambda.io.domain.{LambdaEvent, LambdaDao}
import com.galacticfog.gestalt.utils.servicefactory.GestaltPlugin
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object LambdaWorkerApp {

  def main( args : Array[String] ) : Unit = {

    if ( args.length < 3 ) {
      println( "Illegal number of arguments : " + args.length )
      printUsage()
      sys.exit(1)
    }

    //args.foreach { a => println( "arg : " + a ) }

    val verticleName = args(0)
    val verticleFunction = args(1)
    val eventData = args(2)

    val worker : JSWorker = new JSWorker( verticleName, verticleFunction )

    worker.init()
    worker.start( eventData )
  }

  def printUsage(): Unit = {
    println( "Usage : ")
    println( "\t LambdaWorkerApp <verticleName> <verticleFunction> <eventData>" )
  }

}
