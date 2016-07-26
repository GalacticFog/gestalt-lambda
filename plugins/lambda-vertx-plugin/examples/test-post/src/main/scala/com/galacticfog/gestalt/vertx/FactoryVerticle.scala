package com.galacticfog.gestalt.vertx

import java.io.File

import org.vertx.java.core.{AsyncResult, AsyncResultHandler}
import org.vertx.java.core.json.JsonObject
import org.vertx.java.platform.{PlatformLocator, PlatformManager}
import org.vertx.scala.core.json.JsonObject
import org.vertx.scala.core.json.Json
import org.vertx.scala.core.json.JsonObject
import org.vertx.scala.platform.Verticle
import org.vertx.scala.platform.impl.ScalaVerticle
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.http._
import org.vertx.scala.core._
import org.vertx.java.core.buffer.Buffer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.collection
import scala.collection.Map

import scala.collection.parallel.mutable


class FactoryVerticle extends Verticle {

	//mutable data - ew
	private var policyMap : Map[String,(String,String)] = new collection.mutable.HashMap[String,(String,String)]()
	private var pm : PlatformManager = null;
	private val log : Logger = LoggerFactory.getLogger(classOf[FactoryVerticle])
	def uuid : String = java.util.UUID.randomUUID.toString

	override def start() = {

		log.debug( "FactoryVerticle::start()" )

		pm = PlatformLocator.factory.createPlatformManager()
		val eb = vertx.eventBus

		eb.registerHandler("kafka-address", deployHandler )

		log.debug( "FactoryVerticle started successfully" )
	}

	private val deployHandler  = { message: Message[String] =>
		log.debug("message : " + message.body)

		val jsConfig = new JsonObject( message.body )

		//why doesn't this work
		/*
		val messageOK : Boolean = (
				jsConfig.containsField( "event_label " ) &&
				jsConfig.containsField( "policy_name" ) &&
				jsConfig.containsField( "policy_config" )
			)

		if( !messageOK )
		{
			throw new Exception( "Malformed Policy start event" )
		}
		*/

		val label = jsConfig.getString( "event_label" )
		val name = jsConfig.getString( "policy_name" )
		val config = jsConfig.getObject( "policy_config" )

		val containerId = uuid
		policyMap += (name -> (containerId, label))
		log.debug( s"$name -> ($containerId, $label)" )

		//now we want to spawn a new policy in a worker thread and tell it what to listen for

		val policyConfig = new JsonObject()
		policyConfig.putElement	( "config", config )
		policyConfig.putString( "id", containerId )

		deployVerticle( name, Some(policyConfig) )
	}


	def deployVerticle( verticleName : String, config : Option[JsonObject] = None ) = {
		log.debug( s"deployVerticle( $verticleName )")
		if( config.isDefined )
		{
			log.debug( "config : " + config.get.toString )
		}

		val handler = new AsyncResultHandler[String]() {
			override def handle(ar: AsyncResult[String]) = {
				if (ar.succeeded)
					log.debug(">>> Vertx deployment id is " + ar.result())
				else {
					log.error(">>> Failed to deploy vertx module")
					ar.cause().printStackTrace()
				}
			}
		}

		val verticleHome = sys.env.get( "VERTICLE_HOME" ) getOrElse { "verticles" }
		val vertUrl = new File(verticleHome).toURI().toURL
		//@HACK
		//val resolvedConfig = config getOrElse { null }
		val resolvedConfig = null

		pm.deployVerticle( verticleName, resolvedConfig, Array(vertUrl), 1, null, handler )
	}

	override def stop(): Unit = {
		log.debug( "FactoryVerticle::stop()" )
	}
}
