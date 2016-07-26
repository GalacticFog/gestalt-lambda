package com.galacticfog.gestalt.lambda.io.model

import scalikejdbc._

case class LambdaRepository(
  id: String, 
  isPublic: Boolean, 
  artifactDescription: String, 
  payload: Option[String] = None) {

  def save()(implicit session: DBSession = LambdaRepository.autoSession): LambdaRepository = LambdaRepository.save(this)(session)

  def destroy()(implicit session: DBSession = LambdaRepository.autoSession): Unit = LambdaRepository.destroy(this)(session)

}
      

object LambdaRepository extends SQLSyntaxSupport[LambdaRepository] {

  override val schemaName = Some("public")

  override val tableName = "lambda"

  override val columns = Seq("id", "is_public", "artifact_description", "payload")

  def apply(lr: SyntaxProvider[LambdaRepository])(rs: WrappedResultSet): LambdaRepository = apply(lr.resultName)(rs)
  def apply(lr: ResultName[LambdaRepository])(rs: WrappedResultSet): LambdaRepository = new LambdaRepository(
    id = rs.get(lr.id),
    isPublic = rs.get(lr.isPublic),
    artifactDescription = rs.get(lr.artifactDescription),
    payload = rs.get(lr.payload)
  )
      
  val lr = LambdaRepository.syntax("lr")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[LambdaRepository] = {
    withSQL {
      select.from(LambdaRepository as lr).where.eq(lr.id, id)
    }.map(LambdaRepository(lr.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[LambdaRepository] = {
    withSQL(select.from(LambdaRepository as lr)).map(LambdaRepository(lr.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(LambdaRepository as lr)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Option[LambdaRepository] = {
    withSQL {
      select.from(LambdaRepository as lr).where.append(sqls"${where}")
    }.map(LambdaRepository(lr.resultName)).single.apply()
  }
      
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[LambdaRepository] = {
    withSQL {
      select.from(LambdaRepository as lr).where.append(sqls"${where}")
    }.map(LambdaRepository(lr.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(LambdaRepository as lr).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    isPublic: Boolean,
    artifactDescription: String,
    payload: Option[String] = None)(implicit session: DBSession = autoSession): LambdaRepository = {
    withSQL {
      insert.into(LambdaRepository).columns(
        column.id,
        column.isPublic,
        column.artifactDescription,
        column.payload
      ).values(
        id,
        isPublic,
        artifactDescription,
        payload
      )
    }.update.apply()

    LambdaRepository(
      id = id,
      isPublic = isPublic,
      artifactDescription = artifactDescription,
      payload = payload)
  }

  def save(entity: LambdaRepository)(implicit session: DBSession = autoSession): LambdaRepository = {
    withSQL {
      update(LambdaRepository).set(
        column.id -> entity.id,
        column.isPublic -> entity.isPublic,
        column.artifactDescription -> entity.artifactDescription,
        column.payload -> entity.payload
      ).where.eq(column.id, entity.id)
    }.update.apply()
    entity
  }
        
  def destroy(entity: LambdaRepository)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(LambdaRepository).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
