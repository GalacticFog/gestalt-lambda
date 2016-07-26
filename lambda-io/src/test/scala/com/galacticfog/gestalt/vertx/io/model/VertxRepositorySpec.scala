package com.galacticfog.gestalt.vertx.io.model

import com.galacticfog.gestalt.lambda.io.model.VertxRepository
import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import scalikejdbc._


class VertxRepositorySpec extends Specification {

  "VertxRepository" should {

    val vr = VertxRepository.syntax("vr")

    "find by primary keys" in new AutoRollback {
      val maybeFound = VertxRepository.find("MyString")
      maybeFound.isDefined should beTrue
    }
    "find by where clauses" in new AutoRollback {
      val maybeFound = VertxRepository.findBy(sqls.eq(vr.id, "MyString"))
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = VertxRepository.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = VertxRepository.countAll()
      count should be_>(0L)
    }
    "find all by where clauses" in new AutoRollback {
      val results = VertxRepository.findAllBy(sqls.eq(vr.id, "MyString"))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = VertxRepository.countBy(sqls.eq(vr.id, "MyString"))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = VertxRepository.create(id = "MyString", verticleName = "MyString", artifactName = "MyString", eventFilter = "MyString", timeOut = 1L)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = VertxRepository.findAll().head
      // TODO modify something
      val modified = entity
      val updated = VertxRepository.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = VertxRepository.findAll().head
      VertxRepository.destroy(entity)
      val shouldBeNone = VertxRepository.find("MyString")
      shouldBeNone.isDefined should beFalse
    }
  }

}
        