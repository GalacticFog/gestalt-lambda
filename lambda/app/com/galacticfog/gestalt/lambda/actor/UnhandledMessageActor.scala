package com.galacticfog.gestalt.lambda.actor

import akka.actor.{Actor, ActorLogging, Props, UnhandledMessage}
import akka.event.LoggingReceive
import play.api.{Logger => log}

class UnhandledMessageActor extends Actor with ActorLogging {

  def receive = LoggingReceive { handleRequests }

  def handleRequests : Receive = {
    case message: UnhandledMessage => {
      log.debug(s"CRITICAL! No actors found for message ${message.getMessage}")
    }

    /*
    if (!Environment.isProduction) {
      // Fail fast, fail LOUD
      logger.error("Shutting application down")
      System.exit(-1)
    }
    */
  }
}

object UnhandledMessageActor {
  def props() : Props = Props( new UnhandledMessageActor )
}

