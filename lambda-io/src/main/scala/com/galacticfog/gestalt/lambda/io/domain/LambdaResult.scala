package com.galacticfog.gestalt.lambda.io.domain

import com.galacticfog.gestalt.lambda.io.domain.LambdaContentType.LambdaContentType
import play.api.libs.json.{Reads, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._

object LambdaContentType {

  sealed trait LambdaContentType {
    def name: String
  }

  case object HTML extends LambdaContentType {
    val name = "text/html"
  }

  case object JS extends LambdaContentType {
    val name = "application/js"
  }

  case object TEXT extends LambdaContentType {
    val name = "text/plain"
  }

  def apply( name : String ) : LambdaContentType = {
    name match {
      case HTML.name  => HTML
      case JS.name    => JS
      case TEXT.name | "application/json" => TEXT
      //TODO : is this the right thing to do?
      case _ => TEXT
    }
  }
}

case class LambdaResult( contentType : LambdaContentType, result : String )
