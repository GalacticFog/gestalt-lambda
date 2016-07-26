package com.galacticfog.gestalt.lambda.impl.actor

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import akka.event.LoggingReceive
import com.galacticfog.gestalt.lambda.impl.actor.GFIMessages._
import org.apache.mesos.{MesosSchedulerDriver, Protos}
import org.apache.mesos.Protos.Offer
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.mutable

class OfferActor( driver : MesosSchedulerDriver, taskActor : ActorRef ) extends Actor with ActorLogging {

  val logger = LoggerFactory.getLogger( getClass )

  case class QueuedOffer(
    offer : Protos.Offer,
    queueTime : DateTime = DateTime.now,
    bOffered : Boolean = false
  )

  val TTL = sys.env.getOrElse( "OFFER_TTL", "3" ).toInt

  val offerMap : mutable.HashMap[Protos.OfferID, QueuedOffer] = new mutable.HashMap[Protos.OfferID, QueuedOffer]()

  def receive = LoggingReceive { handleRequests }

  override def preStart = {
  }

  def handleRequests : Receive = {

    case IncomingOffers( offers : Seq[Protos.Offer] ) => {
      logger.trace( s"IncomingOffers(${offers.size})" )
      offers.foreach{ offer =>
        self ! IncomingOffer( offer )
      }
    }
    case IncomingOffer( offer ) => {
      logger.trace( s"IncomingOffer[${offer.getId.getValue}]")

      //schedule a timeout for TTL seconds later
      context.system.scheduler.scheduleOnce( TTL seconds, self, TimeoutOffer( offer ) )

      //TODO : do something more interesting
      taskActor ! IncomingOffer( offer )
    }

    case RemoveOffer( offer ) => {
      logger.trace( s"RemoveOffer[${offer.getId.getValue}]")
      offerMap -= offer.getId
    }

    case RejectOffer( offer ) => {
      logger.trace( s"RejectOffer[${offer.getId.getValue}]")

      val q = offerMap.get( offer.getId )

      if( q.isDefined ) {

        if ( !q.get.bOffered && q.get.queueTime.plusSeconds( TTL ).isBeforeNow ) {
           self ! RemoveOffer( q.get.offer )
           logger.trace( s"[${q.get.offer.getId.getValue}] - TTL expired, declining...")
          driver.declineOffer( offer.getId )
        }
        offerMap.update( offer.getId, q.get.copy( bOffered = false ) )

      }
      else
      {
        logger.trace( s"[${offer.getId.getValue}] - queue offer for " + TTL + " seconds...")
        offerMap += (offer.getId -> new QueuedOffer( offer ))
      }
    }

    case RequestOffer => {
      logger.trace( "RequestOffer()" )

      offerMap.foreach { o =>
        if( !o._2.bOffered ) {
          logger.trace( s"cache hit [${o._2.offer.getId.getValue}]" )
          offerMap.update( o._1, o._2.copy( bOffered = true ) )
          taskActor ! IncomingOffer( o._2.offer )
        }
      }

    }

    case TimeoutOffer( offer ) => {
      logger.trace( s"TimeOutOffer(${offer.getId.getValue})" )

      if( offerMap.get( offer.getId ).isDefined && !offerMap.get( offer.getId ).get.bOffered )
      {
        logger.trace( s"removing timed out offer [${offer.getId.getValue}]")
        driver.declineOffer( offer.getId )
        offerMap -= offer.getId
      }
    }
  }
}

object OfferActor {
  def props( driver: MesosSchedulerDriver, taskActor: ActorRef ) : Props = {
    Props( new OfferActor( driver, taskActor ) )
  }
}
