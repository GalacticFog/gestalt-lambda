package com.galacticfog.gestalt.lambda.impl

import io.vertx.core.json.JsonObject
//import play.api.Logger
import play.api.libs.json.{Json, JsValue}

object Utility {

  def convert( json : JsValue ) : JsonObject = {
    new JsonObject( Json.stringify( json ) )
  }

  def stripTrailingSlash( path : String ) : String = {
    println( "before slash strip: " + path )
    val after = """/$""".r.replaceAllIn( path, "" )
    println( "after slash strip : " + after )
    after
  }
}
