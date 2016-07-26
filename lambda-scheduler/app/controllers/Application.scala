package controllers

import java.util.UUID

import com.galacticfog.lambda.scheduler.{CmdTask, Global}
import play.api._
import play.api.mvc._

class Application extends Controller {

  def launch() = Action { request =>
    // currently, this adds a task to the queue
    // instead, it needs to Ask some actor to launch the appropriate lambda
    Global.taskQueue.enqueue(CmdTask(UUID.randomUUID(),request.body.asText getOrElse "helloWorld","http:://someJar.com/theJar.jar"))
    Accepted("")
  }

  def health() = Action{ Ok("alive") }
}
