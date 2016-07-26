package com.galacticfog.gestalt.vertx.io.model

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import scalikejdbc._


class PolicyTypeRepositorySpec extends Specification {

  "PolicyTypeRepository" should {

    val ptr = PolicyTypeRepository.syntax("ptr")

    "find by primary keys" in new AutoRollback {
      val maybeFound = PolicyTypeRepository.find(1L)
      maybeFound.isDefined should beTrue
    }
    "find by where clauses" in new AutoRollback {
      val maybeFound = PolicyTypeRepository.findBy(sqls.eq(ptr.id, 1L))
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = PolicyTypeRepository.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = PolicyTypeRepository.countAll()
      count should be_>(0L)
    }
    "find all by where clauses" in new AutoRollback {
      val results = PolicyTypeRepository.findAllBy(sqls.eq(ptr.id, 1L))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = PolicyTypeRepository.countBy(sqls.eq(ptr.id, 1L))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = PolicyTypeRepository.create(name = "MyString")
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = PolicyTypeRepository.findAll().head
      // TODO modify something
      val modified = entity
      val updated = PolicyTypeRepository.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = PolicyTypeRepository.findAll().head
      PolicyTypeRepository.destroy(entity)
      val shouldBeNone = PolicyTypeRepository.find(1L)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        