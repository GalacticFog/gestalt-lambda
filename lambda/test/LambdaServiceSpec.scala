import java.util.UUID

import com.galacticfog.gestalt.lambda.io.domain.LambdaDao
import com.galacticfog.gestalt.utils.json.JsonUtils._
import org.specs2.matcher.Matcher
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.test.{TestServer, FakeApplication, PlaySpecification}

class LambdaServiceSpec extends PlaySpecification {

  val additionalConfig = Map(
    "database.host" -> scala.sys.env.getOrElse("TESTDB_HOST","localhost"),
    "database.dbname" -> scala.sys.env.getOrElse("TESTDB_DBNAME","gestaltlambdatestdb"),
    "database.port" -> scala.sys.env.getOrElse("TESTDB_PORT", "5432").toInt,
    "database.username" -> scala.sys.env.getOrElse("TESTDB_USERNAME", "testuser"),
    "database.password" -> scala.sys.env.getOrElse("TESTDB_PASSWORD","testpassword"),
    "database.migrate" -> true,
    "database.clean" -> true,
    "database.shutdownAfterMigrate" -> false
  )
  println(additionalConfig)

  lazy val fakeApp = FakeApplication(additionalConfiguration = additionalConfig, withGlobal = Some(GlobalWithoutMeta))
  lazy val server = TestServer(port = testServerPort, application = fakeApp)

  stopOnFail
  sequential

  step({
    server.start()
  })

  val client = WS.client(fakeApp)

  "Service" should {

    "return OK on /health" in {
      await(client.url(s"http://localhost:${testServerPort}/health").get()).status must equalTo(OK)
    }
  }

  lazy val lambdaId = UUID.randomUUID.toString
  lazy val lambdaName = "testLamba"
  lazy val lambdaJson = Json.obj(
    "id" -> lambdaId,
    "eventFilter" -> UUID.randomUUID.toString,
    "artifactDescription" -> Json.obj(
    "artifactUri" -> "https://s3.amazonaws.com/gfi.lambdas/hello_world.zip",
    "description" -> "super simple hellow world lambda",
    "functionName" -> "hello",
    "handler" -> "hello_world.js",
    "memorySize" -> 1024,
    "cpus" -> 0.2,
    "publish" -> false,
    "role" -> "arn:aws:iam::245814043176:role/GFILambda",
    "runtime" -> "nodejs",
    "timeoutSecs" -> 180
    )
  )

  lazy val lambda = parseAs[LambdaDao]( await( client.url( s"http://localhost:${testServerPort}/lambdas" ).post(lambdaJson) ).json, "Unable to create lambda" )

  "Lambdas" should {

    "return a list of lambdas on /lambdas" in {
      val lambdas = await( client.url( s"http://localhost:${testServerPort}/lambdas" ).get( ) ).json
      lambdas.toString.compareTo( "[]" ) must_== 0
    }

    "allow creation of a lambda with arbitrary id" in {
      lambda.id.get must_== lambdaId
    }
  }

  "Deletes" should {

    "allow deleting a lambda" in {
      await(client.url(s"http://localhost:${testServerPort}/lambdas/${lambdaId}").delete()).status must equalTo(OK)
    }

    "return empty list on /lambdas after delete" in {
      val lambdas = await(client.url(s"http://localhost:${testServerPort}/lambdas").get()).json
      lambdas.toString.compareTo( "[]" ) must_== 0
    }
  }




  step({
    server.stop()
  })

}
