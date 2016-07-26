package com.galacticfog.gestalt.lambda.impl

import play.api.libs.json.{JsValue, Json, JsObject}

case class PolicyReturnEvent( id : String, status : String, payload : String )

object PolicyReturnEvent {
  implicit val policyReturnEventFormat = Json.format[PolicyReturnEvent]

  //TODO : fix this to be enum
  def isSuccess( status : String ) : Boolean = {
    if( status.compareTo( "success" ) == 0 ) { true } else { false }
  }

  def isFailure( status : String ) : Boolean = { !isSuccess( status) }
}
