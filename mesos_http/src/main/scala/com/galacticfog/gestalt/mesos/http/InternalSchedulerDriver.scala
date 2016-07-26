package com.galacticfog.gestalt.lambda.impl.http

import java.io._
import java.lang.Exception

import org.apache.mesos.Protos.SlaveID
import org.apache.mesos.Protos._
import org.apache.mesos.v1.scheduler.Protos.Event
import org.apache.mesos.{SchedulerDriver, Protos, Scheduler}
import play.api.libs.ws.WSResponse
import play.api.{Logger => log}
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import com.google.common.base.Preconditions.checkState
import org.apache.mesos.Protos.Status.DRIVER_ABORTED
import org.apache.mesos.Protos.Status.DRIVER_NOT_STARTED
import org.apache.mesos.Protos.Status.DRIVER_RUNNING
import org.apache.mesos.Protos.Status.DRIVER_STOPPED
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import collection.JavaConverters._

import com.galacticfog.gestalt.lambda.impl.http._

class InternalSchedulerDriver( scheduler : Scheduler, frameworkInfo : FrameworkInfo, master : String, implicitAcknowledgement : Boolean = true, credential : Credential = null) extends SchedulerDriver {

    //TODO : yuck - mutable
    var frameworkId : Protos.FrameworkID = null
    var mesosStreamId : String = null

    val callBuilder = new (com.ning.http.client.AsyncHttpClientConfig.Builder)()
    val callClient = new play.api.libs.ws.ning.NingWSClient(callBuilder.build())

    def clientCall( path : String, payload : String ) : Future[WSResponse] = {
        callClient.url(s"http://${master}${path}")
          .withRequestTimeout(3000)
          .withHeaders(
            ("Accept"->"application/json"),
            ("Content-Type"->"application/json"),
            ("Mesos-Stream-Id"->mesosStreamId)
          ).post( payload )
    }

    override def start : Status =
    {
        DRIVER_RUNNING
    }

    override def stop ( failover : Boolean ) : Status =
    {
        DRIVER_RUNNING
    }


    override def stop() : Status = {
        DRIVER_RUNNING
    }

    override def abort() : Status = {
        DRIVER_RUNNING
    }

    override def join() : Status = {
        DRIVER_RUNNING
    }

    override def run() : Status = {

        //let's test the SSE
        val builder = new ( com.ning.http.client.AsyncHttpClientConfig.Builder )( )
        val client = new play.api.libs.ws.ning.NingWSClient( builder.build( ) )

        def process = Iteratee.foreach { chunk: Array[Byte] => {

            try {

                val pieces = new String( chunk ).split( "\n" )
                val dataSize = pieces( 0 )
                val restOfBlobString = pieces.slice( 1, pieces.size ).mkString

                println( "size : " + dataSize )
                println( "blob : " + restOfBlobString )

                val blob = restOfBlobString.getBytes
                val data = blob.slice( 0, dataSize.toInt )

                val dataString = new String( data )
                println( "data : " + dataString )

                val jsonBlob = Json.parse( dataString )

                val jsonType = ( jsonBlob \ "type" ).as[String]

                jsonType match {
                    case "OFFERS" => {
                        log.debug( "parsing offers..." )
                        val offerEvent = jsonBlob.as[OfferEvent]
                        val offers = offerEvent.offers.offers.map( _.toProtos )
                        scheduler.resourceOffers( this, offers.asJava )
                    }
                    case "SUBSCRIBED" => {
                        log.debug( "parsing subscription..." )
                        val subEvent = jsonBlob.as[SubscribedResponseEnvelope]
                        frameworkId = subEvent.subscribed.framework_id.toProtos

                    }
                    case _ => {
                        log.debug( "IGNORING message type : " + jsonType )
                    }
                }
            } catch {
                case ex: Exception => {
                    log.error( "FAILED TO PARSE : " + ex.getMessage )
                    ex.printStackTrace( )
                }
            }
        }
        }

        val subJson = Json.obj(
            "type" -> "SUBSCRIBE",
            "subscribe" -> Json.obj(
                "framework_info" -> Json.obj(
                    "user" -> frameworkInfo.getUser,
                    "name" -> frameworkInfo.getName
                )
            )
        )

        client.url( s"http://${master}/api/v1/scheduler" )
          .withRequestTimeout( -1 )
          .withHeaders( ( "Accept" -> "application/json" ), ( "Content-Type" -> "application/json" ) )
          .postAndRetrieveStream( subJson ) { headers =>
            mesosStreamId = headers.headers.get("Mesos-Stream-Id").map( _.head ).getOrElse( "NONE" )
            if( headers.status ==  200 )
                process
            else
              ???
        }

        DRIVER_RUNNING
    }

    override def requestResources(requests : java.util.Collection[Request] ) : Status = {
        DRIVER_RUNNING
    }

    override def launchTasks(offerIds : java.util.Collection[Protos.OfferID], tasks : java.util.Collection[Protos.TaskInfo], filters : Filters ) : Status = {
        DRIVER_RUNNING
    }

    override def launchTasks(offerIds : java.util.Collection[Protos.OfferID], tasks : java.util.Collection[Protos.TaskInfo] ) : Status = {

        val accept = Request.launch( tasks.asScala.toList, offerIds.asScala.toList, frameworkId.getValue )

        val acceptBody = Json.toJson( accept )
        log.debug( "ACCEPT BODY : " + acceptBody.toString )

        val fut = clientCall( "/api/v1/scheduler", acceptBody.toString )
        fut.onSuccess{
            case response => {
                println( "DECLINE RESPONSE : ")
                println( response.body )
            }
        }
        fut.onFailure{
            case response => {
                println( "DECLINE FAILED : " )
                println( response.getMessage )
            }
        }

        DRIVER_RUNNING
    }

    override def launchTasks(offerId: Protos.OfferID, tasks : java.util.Collection[Protos.TaskInfo], filters : Filters ) : Status = {
        DRIVER_RUNNING
    }

    override def launchTasks(offerId : Protos.OfferID, tasks : java.util.Collection[Protos.TaskInfo] ) : Status = {
        DRIVER_RUNNING
    }

    override def killTask(taskID : Protos.TaskID ) : Status = {
        DRIVER_RUNNING
    }

    override def acceptOffers( offerIds : java.util.Collection[Protos.OfferID], operations : java.util.Collection[Protos.Offer.Operation], filters : Filters )  : Status = {
        DRIVER_RUNNING
    }

    override def declineOffer(offerId : Protos.OfferID, filters : Filters) : Status = {

        //TODO : Filters?
        val declineMessage = s"""{
                                 "framework_id"    : {"value" : "${frameworkId}"},
                                 "type"            : "DECLINE",
                                 "Decline"         : {
                                   "offer_ids" : [ {"value" : "${offerId.getValue}"} ]
                                 }
                               }"""


        val fut = clientCall( "/api/v1/scheduler", declineMessage )
        fut.onSuccess{
            case response => {
                println( "DECLINE RESPONSE : ")
                println( response.body )
            }
        }
        fut.onFailure{
            case response => {
                println( "DECLINE FAILED : " )
                println( response.getMessage )
            }
        }


        DRIVER_RUNNING
    }

    override def declineOffer(offerId : Protos.OfferID) : Status = {

        //TODO : Filters?
        val declineMessage = s"""{
                                 "framework_id"    : {"value" : "${frameworkId}"},
                                 "type"            : "DECLINE",
                                 "Decline"         : {
                                   "offer_ids" : [ {"value" : "${offerId.getValue}"} ]
                                 }
                               }"""

        val jsonDecline = Json.obj(
            "framework_id" -> Json.obj( "value" -> frameworkId.getValue ),
            "type" -> "DECLINE",
            "decline" -> Json.obj(
                "offer_ids" -> Json.arr(
                    Json.obj( "value" -> offerId.getValue )
                )
            )
        )

        val fut = clientCall( "/api/v1/scheduler", jsonDecline.toString )

        /*
        fut.onSuccess{
            case response => {
                println( "DECLINE RESPONSE : ")
                println( response.body )
            }
        }
        */

        fut.onFailure{
            case response => {
                response.printStackTrace()
                log.error( "DECLINE CALL FAILED : " )
                log.error( response.getMessage )
            }
        }

        DRIVER_RUNNING
    }

    override def reviveOffers() : Status = {
        DRIVER_RUNNING
    }

    override def suppressOffers() : Status = {
        DRIVER_RUNNING
    }

    override def acknowledgeStatusUpdate(status : TaskStatus ) : Status = {
        DRIVER_RUNNING
    }

    override def sendFrameworkMessage(executorId : Protos.ExecutorID, slaveId : SlaveID, data : Array[Byte] ) : Status = {
        DRIVER_RUNNING
    }

    override def reconcileTasks( statuses : java.util.Collection[TaskStatus]) : Status = {
        DRIVER_RUNNING
    }
}
