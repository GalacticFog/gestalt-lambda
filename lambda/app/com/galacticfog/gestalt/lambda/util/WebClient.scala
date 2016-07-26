package com.galacticfog.gestalt.lambda.util

import akka.util.Timeout
import org.joda.time.DateTime
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

case class UnauthorizedAPIException(resp: String) extends Throwable(resp)
case class ForbiddenAPIException(resp: String) extends Throwable(resp)
case class UnknownAPIException(resp: String) extends Throwable(resp)
case class ResourceNotFoundException(url: String) extends Throwable("resource not found: " + url)


class WebClient(val client: WSClient, val protocol: String, val hostname: String, val port: Int, val username: String, val password: String) {

  private val TASK_TIMEOUT = 10
  val timeout = Timeout( TASK_TIMEOUT seconds )

  def processResponse( response: WSResponse ): JsValue = {
    println( "RESPONSE CODE(" + response.status + " : " + response.statusText + " )" )
    /*
    println( "RESPONSE HEADERS :" )
    response.allHeaders.foreach( h => {
      println( h._1 + ":" )
      h._2.foreach( hh => println( hh ) )
      }
    )
    */

    response.status match {
      case x if x == 204 => Json.obj( "status" -> "success" )
      case x if x >= 200 && x < 300 => response.json
      case x if x == 401 => throw new UnauthorizedAPIException( response.body )
      case x if x == 403 => throw new ForbiddenAPIException( response.body )
      case x if x == 404 => throw new ResourceNotFoundException( response.body )
      case _ => throw new UnknownAPIException( s"${response.status}: ${response.body}" )
    }
  }

  private def removeLeadingSlash( endpoint: String ) = {
    if ( endpoint.startsWith( "/" ) ) endpoint.substring( 1 )
    else endpoint
  }

  private def makeUrl( endpoint : String ) : String  = {
    s"${protocol}://${hostname}:${port}/${removeLeadingSlash( endpoint )}"
  }

  private def genRequestNoHeaders( endpoint : String ) : WSRequestHolder = {
    val href = makeUrl( endpoint )
    client.url( href )
  }

  private def genRequest( endpoint: String ): WSRequestHolder = {
    val href = makeUrl( endpoint )
    genBareRequest( href )
  }

  def genBareRequest( href : String ) : WSRequestHolder = {
    client.url( href )
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .withAuth(username = username, password = password, scheme = WSAuthScheme.BASIC)
  }

  def get(endpoint: String, params : Option[Map[String,String]] = None ) : Future[JsValue] = {
    val request = genRequest( endpoint )
    val finalRequest = params match {
      case Some( s ) => request.withQueryString( s.toSeq: _* )
      case None => request
    }
    finalRequest.get( ).map( processResponse )
  }

  def easyGet( endpoint : String, params : Option[Map[String,String]] = None ) : Try[JsValue] = Try {
    val future =  get( endpoint, params )
    Await.result( future, timeout.duration )
  }

  def post(endpoint: String, payload: JsValue): Future[JsValue] = genRequest(endpoint).post(payload).map(processResponse)

  def post(endpoint: String): Future[JsValue] = genRequest(endpoint).post("").map(processResponse)

  def easyPost( endpoint : String, payload : JsValue) : Try[JsValue] = Try{
    println( s"easyPost( $endpoint) : ${payload.toString()}" )
    Await.result( post( endpoint, payload ), timeout.duration )
  }

  def easyPost( endpoint : String ) : Try[JsValue] = Try{
    println( s"easyPost( $endpoint)" )
    Await.result( post( endpoint ), timeout.duration )
  }

  def put(endpoint: String, payload: JsValue): Future[JsValue] = genRequest(endpoint).put(payload).map(processResponse)

  def easyPut( endpoint : String, payload : JsValue) : Try[JsValue] = Try{
    println( s"easyPut( $endpoint) : ${payload.toString()}" )
    Await.result( put( endpoint, payload ), timeout.duration )
  }

  def delete(endpoint: String): Future[JsValue] = genRequestNoHeaders(endpoint).delete().map(processResponse)

  def easyDelete( endpoint : String ) : Try[JsValue] = Try{
    println( s"easyDelete( $endpoint)" )
    Await.result( delete( endpoint ), timeout.duration )
  }
}

object WebClient {
  def apply(wsclient: WSClient, protocol: String, hostname: String, port: Int, username: String, password: String) =
    new WebClient(client = wsclient, protocol = protocol, hostname = hostname, port = port, username = username, password = password)
}
