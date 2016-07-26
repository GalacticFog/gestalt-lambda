package com.galacticfog.gestalt.lambda.impl

import java.io.File
import java.net.URL

import com.galacticfog.gestalt.lambda.io.domain.{LambdaEvent, LambdaDao}
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import io.vertx.core._
import io.vertx.core.impl.JavaVerticleFactory

//import org.vertx.scala.core.eventbus.Message
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import play.api.libs.json._
//import play.api.{Logger => log}

import scala.collection.Map

case class VertxDao(
  id : Option[String],
  verticleName : String,
  artifactUri : String,
  timeOut : Long,
  eventFilter : String,
  policyConfig : JsValue,
  payload : Option[JsValue]
)

object VertxDao {
  implicit val vertxFormats = Json.format[VertxDao]
}


class VertxLambdaAdapter extends LambdaAdapter {

  //mutable data - ew
  private var policyMap : Map[String,String] = new collection.mutable.HashMap[String,String]()

  def uuid : String = java.util.UUID.randomUUID.toString

  def getPluginName : String = "VertxLambdaAdapter"

  def createLambda( data : LambdaDao ) : String = {
    println( "VertxAdapter::createLambda" )
    "FakeString"
  }

  def deleteLambda( data : LambdaDao ) : Unit = {
    println( "VertxAdapter::deleteLambda" )
  }

  def invokeLambda( data : LambdaDao, event : LambdaEvent ) : String = {

    val verticleReturnHandler = new Handler[Message[String]]() {
      override def handle( message: Message[String] ): Unit = {
        println( "Received Verticle Return Value message : " + message.body )

        //TODO : do something with the return value.  What?

      }
    }

    val verticleKafkaHandler = new Handler[Message[String]]() {
      override def handle( message: Message[String] ): Unit = {
        println( "Received Verticle Kafka message : " + message.body )

        //TODO : hookup our event bus here
        //writer.write( Json.parse( message.body ).as[GestaltEvent] )
      }
    }

    Vertx.vertx.eventBus.consumer( "module-return", verticleReturnHandler )
    Vertx.vertx.eventBus().consumer( "kafka", verticleKafkaHandler )

    println( "event payload : " + event.data )

    val policy : VertxDao = data.artifactDescription.validate[VertxDao] match {
      case JsSuccess( s, _ ) => s
      case err@JsError(_) => {
        throw new Exception( "Error parsing vertx info : " + Json.toJson( JsError.toFlatJson(err) ) )
      }
    }

    val policyConfig : JsValue = policy.policyConfig

    //now we want to spawn a new policy in a worker thread and tell it what to listen for

    //1.) Prepare the config and payloads for the verticle
    val selfConfig : JsonObject = Utility.convert( policyConfig )
    val modConfigJson = new JsonObject()
    modConfigJson.put( "id", policy.id.get )
    modConfigJson.put( "config", selfConfig )
    modConfigJson.put( "payload", Utility.convert( event.data ) )

    //TODO : fix downstream or get this included in the event
    //modConfigJson.putElement( "task", Utility.convert( Json.parse( event.task ) ) )

    println( s"deployVerticle( ${policy.verticleName}} )" )
    println( "config : " + policyConfig.toString )

    val handler = new AsyncResultHandler[String]( ) {
      override def handle( ar: AsyncResult[String] ) = {
        if ( ar.succeeded ) {
          println( ">>> Vertx deployment id is " + ar.result( ) )

          //TODO : if this is goin to work, the artifact names must be unique to the factory, or else we have to
          //make them unique or store the ID in the DB?
          val deploymentId = ar.result()
          policyMap += ( policy.id.get -> deploymentId )
          println( s"${policy.id.get} -> $deploymentId" )
        }
        else {
          println( ">>> Failed to deploy vertx module" )
          ar.cause( ).printStackTrace( )
        }
      }
    }

    //2.) prepare the URL for the file
    //TODO : this should support more than just file type uri's
    val url = new URL(policy.artifactUri)
    val modFile = new File( url.getFile )

    //3.) prepare the options for the verticle
    //  - TODO : there are other options that we can explore
    val options = new DeploymentOptions()
    options.setConfig( modConfigJson )
    //this is important becasue we don't know what type of work the verticles are going to do
    //so we can't block the vent loop
    options.setWorker( true )

    //-----
    //4.) extend the classpath to dynamically load the verticle
    //  - if you don't do this dynamic jar loading loading then the verticle class won't be found
    //  - the classLoader needs to be the same among the dynamic loader and the verticle factory
    //    if you don't use the same loader you won't be able to cast it to a Verticle because the JVM
    //    doesn't think it's the same class
    //-------------------

    DynLoader.extendClasspath( modFile, this.getClass.getClassLoader )
    val factory = new JavaVerticleFactory()
    val verticle = factory.createVerticle( policy.verticleName, this.getClass.getClassLoader )

    //5.) deploy the verticle
    Vertx.vertx.deployVerticle( verticle, options )
    "TODO_FIX"
  }

  def getNewName( name : String, suffix : String ) : String = {
    val parts = name.split("\\.")
    parts.slice(0, parts.length-1).mkString(".") + "." + suffix
  }


  //TODO : stop?
  //Vertx.vertx.undeploy( policyId, handler )

  def deployModule( moduleName : String, config : Option[JsonObject] = None ) = {

    val handler = new AsyncResultHandler[String]() {
      override def handle(ar: AsyncResult[String]) = {
        if (ar.succeeded)
          println(">>> Vertx deployment id is " + ar.result())
        else {
          println(">>> Failed to deploy vertx module")
          ar.cause().printStackTrace()
        }
      }
    }

    val resolvedConfig = config getOrElse { null }
    val options = new DeploymentOptions()
    options.setConfig( resolvedConfig )
    Vertx.vertx.deployVerticle( moduleName, options, handler )
  }
}

