package com.galacticfog.gestalt.lambda.io.domain

import com.galacticfog.gestalt.lambda.io.domain.LambdaContentType.LambdaContentType
import com.galacticfog.gestalt.lambda.io.model.ResultRepository
import com.galacticfog.gestalt.lambda.utils.SecureIdGenerator
import org.joda.time.DateTime
import play.api.libs.json.{Reads, JsValue, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scalikejdbc._
import ResultRepository.rr

import scala.util.Success

case class ResultDao( lambdaId : String, executionId : String, contentType : LambdaContentType, result : String, log : Option[String], executionTime : Option[DateTime] )

object ResultDao {

  //implicit val resultDaoFormat = Json.format[ResultDao]
  implicit val resultDaoReads : Reads[ResultDao] =
    (
      ( __ \ "lambdaId" ).read[String] and
      ( __ \ "executionId" ).read[String] and
      ( __ \ "contentType" ).read[String].map( LambdaContentType(_) ) and
      ( __ \ "result" ).read[String] and
      ( __ \ "log" ).readNullable[String] and
      ( __ \ "executionTime" ).readNullable[DateTime]
    )(ResultDao.apply _)

  implicit val resultDaoWrites : Writes[ResultDao] = new Writes[ResultDao] {
    def writes( result : ResultDao ) : JsValue = {

      var obj = Json.obj(
        "lambdaId" -> result.lambdaId,
          "executionId" -> result.executionId,
          "contentType" -> result.contentType.name,
          "result" -> result.result
      )

      if( result.log.isDefined )
      {
        obj = obj + ("log" -> Json.toJson( result.log.get) )
      }
      if( result.executionTime.isDefined )
      {
        obj = obj + ("executionTime" -> Json.toJson( result.executionTime.get ) )
      }

      obj
    }
  }

  private val ID_LENGTH = 24

  def create(
              lambdaId : String,
              executionId : String,
              result : String,
              contentType : LambdaContentType,
              log : Option[String] = None
              ) : ResultDao = {

    val id = SecureIdGenerator.genId62( ID_LENGTH )
    make(
      ResultRepository.create(
        lambdaId = lambdaId,
        executionId = executionId,
        executionTime = DateTime.now,
        contentType = contentType.name,
        result = result,
        log = log
      )
    )
  }

  def make( lr : ResultRepository ) : ResultDao = {
    new ResultDao(
      lambdaId = lr.lambdaId,
      executionId = lr.executionId,
      contentType = LambdaContentType( lr.contentType ),
      result = lr.result,
      executionTime = Some(lr.executionTime),
      log = lr.log
    )
  }

  def findAll : Seq[ResultDao] = {
    ResultRepository.findAll.map(make(_))
  }

  def delete( id : String ) : Unit = {
    ResultRepository.find( id ) match {
      case Some(s) => s.destroy
      case None => {
        throw new Exception( s"Result not found with id $id" )
      }
    }
  }

  //TODO : this may need to return a list at some point depending on how we handle the metaContext issue
  def find( executionId : String )(implicit session : DBSession = AutoSession) : Option[ResultDao] = {
    withSQL {
      select.from(ResultRepository as rr).where.eq(rr.executionId, executionId)
    }.map(ResultRepository(rr)).single.apply().map(make(_))
  }

  def findById( id : String ) : Option[ResultDao] = {
    ResultRepository.find(id).map(make(_))
  }

}
