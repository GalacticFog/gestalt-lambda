package com.galacticfog.gestalt.vertx.com.galacticfog.gestalt.vertx.io.model

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import scalikejdbc._


class PolicyRepositorySpec extends Specification {

  "PolicyRepository" should {

    val pr = PolicyRepository.syntax("pr")

    "find by primary keys" in new AutoRollback {
      val maybeFound = PolicyRepository.find("MyString")
      maybeFound.isDefined should beTrue
    }
    "find by where clauses" in new AutoRollback {
      val maybeFound = PolicyRepository.findBy(sqls.eq(pr.id, "MyString"))
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = PolicyRepository.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = PolicyRepository.countAll()
      count should be_>(0L)
    }
    "find all by where clauses" in new AutoRollback {
      val results = PolicyRepository.findAllBy(sqls.eq(pr.id, "MyString"))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = PolicyRepository.countBy(sqls.eq(pr.id, "MyString"))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = PolicyRepository.create(id = "MyString", handlerId = "MyString", policyName = "MyString", artifactName = "MyString", eventFilter = "MyString")
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = PolicyRepository.findAll().head
      // TODO modify something
      val modified = entity
      val updated = PolicyRepository.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = PolicyRepository.findAll().head
      PolicyRepository.destroy(entity)
      val shouldBeNone = PolicyRepository.find("MyString")
      shouldBeNone.isDefined should beFalse
    }
  }

}
        