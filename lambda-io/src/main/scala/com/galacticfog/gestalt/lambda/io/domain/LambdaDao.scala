package com.galacticfog.gestalt.lambda.io.domain

import java.util.UUID

import com.galacticfog.gestalt.lambda.io.model.LambdaRepository
import play.api.libs.json.{JsValue, Json}
import scalikejdbc._
import LambdaRepository.lr

import scala.util.Success

//@deprecated - eventFilter is deprecated
case class LambdaDao( id : Option[String], eventFilter: Option[String] = None, public : Option[Boolean], artifactDescription : JsValue, payload : Option[String] ) {
  def create : LambdaDao = {
    LambdaDao.create(
      inId = id,
      public = public.getOrElse( false ),
      artifactDescription = artifactDescription,
      payload = payload
    )
  }

  def update : LambdaDao = {

    val lr = LambdaRepository.find( id.get ) getOrElse {
      throw new Exception( "Could not find lambda with id : " + id.get )
    }

    LambdaDao.make(
      lr.copy(
        id = id.get,
        isPublic = public.getOrElse( false ),
        artifactDescription = Json.stringify( artifactDescription ),
        payload = payload
      ).save
    )
  }
}

object LambdaDao {

  implicit val lambdaDaoFormat = Json.format[LambdaDao]

  def create(
    inId : Option[String],
    public : Boolean,
    artifactDescription : JsValue,
    payload : Option[String] = None
  ) : LambdaDao = {

    val id = inId getOrElse UUID.randomUUID.toString

    make(
      LambdaRepository.create(
        id = id,
        isPublic = public,
        artifactDescription = Json.stringify( artifactDescription ),
        payload = payload
      )
    )
  }

  def make( lr : LambdaRepository ) : LambdaDao = {
    new LambdaDao(
      id = Some(lr.id),
      public = Some(lr.isPublic),
      artifactDescription = Json.parse( lr.artifactDescription ),
      payload = lr.payload
    )
  }

  def findAll : Seq[LambdaDao] = {
    LambdaRepository.findAll.map(make(_))
  }

  def delete( id : String ) : Unit = {
    LambdaRepository.find( id ) match {
      case Some(s) => s.destroy
      case None => {
        throw new Exception( s"Lambda not found with id $id" )
      }
    }
  }

  def find( id : String ) : Option[LambdaDao] = {
    LambdaRepository.find(id).map(make(_))
  }

  def findById( id : String ) : Option[LambdaDao] = {
    LambdaRepository.find(id).map(make(_))
  }

}
