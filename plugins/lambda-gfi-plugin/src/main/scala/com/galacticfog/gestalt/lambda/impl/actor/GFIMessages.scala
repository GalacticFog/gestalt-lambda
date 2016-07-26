package com.galacticfog.gestalt.lambda.impl.actor

import akka.actor.ActorRef
import com.galacticfog.gestalt.lambda.impl.GFILambdaInfo
import com.galacticfog.gestalt.lambda.io.domain.{LambdaDao, LambdaEvent}
import org.apache.mesos.Protos

import scala.concurrent.Future

object GFIMessages {

  sealed trait GFIMessage

  case class InvokeLambda(
    lambda : GFILambdaInfo,
    event : LambdaEvent,
    uuid : String,
    env : Future[Map[String,String]],
    senderActor : Option[ActorRef] = None,
    creds : Option[String] = None ) extends GFIMessage

  case class TimeoutLambda( uuid : String ) extends GFIMessage
  case class InvalidateCache( lambda : LambdaDao ) extends GFIMessage
  case object CheckCache extends GFIMessage


  case class QueueLambda( lambda : GFILambdaInfo, event : LambdaEvent, uuid : String, env : Future[Map[String,String]], senderActor : ActorRef, creds : Option[String] ) extends GFIMessage
  case class MatchOffer( offer : OfferMatch ) extends GFIMessage
  case class LaunchTasks( offer : OfferMatch ) extends GFIMessage
  case object RequestOffer extends GFIMessage
  case class RemoveOffer( offer : Protos.Offer ) extends GFIMessage
  case class TimeoutOffer( offer : Protos.Offer ) extends GFIMessage


  case class IncomingOffer( offer : Protos.Offer ) extends GFIMessage
  case class IncomingOffers( offers : Seq[Protos.Offer] ) extends GFIMessage
  case class RejectOffer( offer : Protos.Offer ) extends GFIMessage
  case object CheckOffers extends GFIMessage


  case object TestMessage extends GFIMessage
  case object ShutdownScheduler extends GFIMessage

}
