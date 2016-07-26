package com.galacticfog.gestalt.lambda.io.model

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import scalikejdbc._
import org.joda.time.{DateTime}


class ResultRepositorySpec extends Specification {

  "ResultRepository" should {

    val rr = ResultRepository.syntax("rr")

    "find by primary keys" in new AutoRollback {
      val maybeFound = ResultRepository.find("MyString")
      maybeFound.isDefined should beTrue
    }
    "find by where clauses" in new AutoRollback {
      val maybeFound = ResultRepository.findBy(sqls.eq(rr.executionId, "MyString"))
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = ResultRepository.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = ResultRepository.countAll()
      count should be_>(0L)
    }
    "find all by where clauses" in new AutoRollback {
      val results = ResultRepository.findAllBy(sqls.eq(rr.executionId, "MyString"))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = ResultRepository.countBy(sqls.eq(rr.executionId, "MyString"))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = ResultRepository.create(executionId = "MyString", lambdaId = "MyString", executionTime = DateTime.now, contentType = "MyString", result = "MyString")
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = ResultRepository.findAll().head
      // TODO modify something
      val modified = entity
      val updated = ResultRepository.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = ResultRepository.findAll().head
      ResultRepository.destroy(entity)
      val shouldBeNone = ResultRepository.find("MyString")
      shouldBeNone.isDefined should beFalse
    }
  }

}
        