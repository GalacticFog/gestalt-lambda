package com.galacticfog.gestalt.lambda.io.model

import scalikejdbc._
import org.joda.time.{DateTime}

case class ResultRepository(
  executionId: String, 
  lambdaId: String, 
  executionTime: DateTime, 
  contentType: String, 
  result: String, 
  log: Option[String] = None) {

  def save()(implicit session: DBSession = ResultRepository.autoSession): ResultRepository = ResultRepository.save(this)(session)

  def destroy()(implicit session: DBSession = ResultRepository.autoSession): Unit = ResultRepository.destroy(this)(session)

}
      

object ResultRepository extends SQLSyntaxSupport[ResultRepository] {

  override val schemaName = Some("public")

  override val tableName = "result"

  override val columns = Seq("execution_id", "lambda_id", "execution_time", "content_type", "result", "log")

  def apply(rr: SyntaxProvider[ResultRepository])(rs: WrappedResultSet): ResultRepository = apply(rr.resultName)(rs)
  def apply(rr: ResultName[ResultRepository])(rs: WrappedResultSet): ResultRepository = new ResultRepository(
    executionId = rs.get(rr.executionId),
    lambdaId = rs.get(rr.lambdaId),
    executionTime = rs.get(rr.executionTime),
    contentType = rs.get(rr.contentType),
    result = rs.get(rr.result),
    log = rs.get(rr.log)
  )
      
  val rr = ResultRepository.syntax("rr")

  override val autoSession = AutoSession

  def find(executionId: String)(implicit session: DBSession = autoSession): Option[ResultRepository] = {
    withSQL {
      select.from(ResultRepository as rr).where.eq(rr.executionId, executionId)
    }.map(ResultRepository(rr.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[ResultRepository] = {
    withSQL(select.from(ResultRepository as rr)).map(ResultRepository(rr.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(ResultRepository as rr)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Option[ResultRepository] = {
    withSQL {
      select.from(ResultRepository as rr).where.append(sqls"${where}")
    }.map(ResultRepository(rr.resultName)).single.apply()
  }
      
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[ResultRepository] = {
    withSQL {
      select.from(ResultRepository as rr).where.append(sqls"${where}")
    }.map(ResultRepository(rr.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(ResultRepository as rr).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    executionId: String,
    lambdaId: String,
    executionTime: DateTime,
    contentType: String,
    result: String,
    log: Option[String] = None)(implicit session: DBSession = autoSession): ResultRepository = {
    withSQL {
      insert.into(ResultRepository).columns(
        column.executionId,
        column.lambdaId,
        column.executionTime,
        column.contentType,
        column.result,
        column.log
      ).values(
        executionId,
        lambdaId,
        executionTime,
        contentType,
        result,
        log
      )
    }.update.apply()

    ResultRepository(
      executionId = executionId,
      lambdaId = lambdaId,
      executionTime = executionTime,
      contentType = contentType,
      result = result,
      log = log)
  }

  def save(entity: ResultRepository)(implicit session: DBSession = autoSession): ResultRepository = {
    withSQL {
      update(ResultRepository).set(
        column.executionId -> entity.executionId,
        column.lambdaId -> entity.lambdaId,
        column.executionTime -> entity.executionTime,
        column.contentType -> entity.contentType,
        column.result -> entity.result,
        column.log -> entity.log
      ).where.eq(column.executionId, entity.executionId)
    }.update.apply()
    entity
  }
        
  def destroy(entity: ResultRepository)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(ResultRepository).where.eq(column.executionId, entity.executionId) }.update.apply()
  }
        
}
