package com.galacticfog.gestalt.lambda.io.domain

import play.api.libs.json.{Json, JsValue}

case class LambdaEvent( eventName : String, data : JsValue )

object LambdaEvent {
  implicit val lambdaEventFormat = Json.format[LambdaEvent]
}
