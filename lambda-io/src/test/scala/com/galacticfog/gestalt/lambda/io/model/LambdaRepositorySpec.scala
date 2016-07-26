package com.galacticfog.gestalt.lambda.io.model

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import scalikejdbc._


class LambdaRepositorySpec extends Specification {

  "LambdaRepository" should {

    val lr = LambdaRepository.syntax("lr")

    "find by primary keys" in new AutoRollback {
      val maybeFound = LambdaRepository.find("MyString")
      maybeFound.isDefined should beTrue
    }
    "find by where clauses" in new AutoRollback {
      val maybeFound = LambdaRepository.findBy(sqls.eq(lr.id, "MyString"))
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = LambdaRepository.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = LambdaRepository.countAll()
      count should be_>(0L)
    }
    "find all by where clauses" in new AutoRollback {
      val results = LambdaRepository.findAllBy(sqls.eq(lr.id, "MyString"))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = LambdaRepository.countBy(sqls.eq(lr.id, "MyString"))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = LambdaRepository.create(id = "MyString", isPublic = false, artifactDescription = "MyString")
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = LambdaRepository.findAll().head
      // TODO modify something
      val modified = entity
      val updated = LambdaRepository.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = LambdaRepository.findAll().head
      LambdaRepository.destroy(entity)
      val shouldBeNone = LambdaRepository.find("MyString")
      shouldBeNone.isDefined should beFalse
    }
  }

}
        