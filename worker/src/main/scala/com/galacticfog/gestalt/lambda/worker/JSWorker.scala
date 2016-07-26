package com.galacticfog.gestalt.lambda.worker

import java.net.URL
import java.util.Scanner
import javax.script.{ScriptException, ScriptEngineManager, ScriptEngine}

import jdk.nashorn.api.scripting.ScriptObjectMirror
import play.api.libs.json.Json

import scala.concurrent.Future

class JSWorker( verticleName : String, startFunctionName : String ) {

  private val JVM_NPM = "npm-js.js"

  val engine : ScriptEngine = init()
  var exports : ScriptObjectMirror = null

  def init() : ScriptEngine = {
    val mgr : ScriptEngineManager = new ScriptEngineManager(null)
    val engine = mgr.getEngineByName("nashorn")


    // NOTE : This jvm-npm business allows for using a Node.js style syntax for importing external scripts.
    // These scripts need to be findable on the classpath for this script, which means that they need to be bundled in the
    // zip file along with the main handler script.  The syntax is simply :  require( 'filenameWithoutJSPostfix' )

    val url : URL = getClass().getClassLoader().getResource(JVM_NPM)
    if (url == null) {
      throw new IllegalStateException("Cannot find " + JVM_NPM + " on classpath")
    }

    try {
      //TODO ; This should probably move somewhere else, so we don't do it multiple times
      ClasspathFileResolver.init()

      val scanner : Scanner = new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A")
      val jvmNpm : String = scanner.next()
      val jvmNpmPath : String = ClasspathFileResolver.resolveFilename(JVM_NPM)
      val fullPath  = jvmNpm +  "\n//# sourceURL=" + jvmNpmPath
      engine.eval(fullPath)
    }
    catch {
      case (e : Exception ) => {
        e.printStackTrace()
        throw new IllegalStateException(e)
      }
    }

    try {

      // Put the globals in
      val globs : String =
        "var console = require('console');" +
         "var parent = this;" +
         "var global = this;"

    /*
    if (ADD_NODEJS_PROCESS_ENV) {
      globs += "var process = {}; process.env=java.lang.System.getenv();"
    }
    */

      engine.eval(globs)
    }
    catch {

      case e : ScriptException =>
      throw new IllegalStateException("Failed to eval: " + e.getMessage(), e)
    }

    engine
  }

  def functionExists(functionName : String) : Boolean = {
    val som : Object = exports.getMember(functionName)
    som != null && !som.toString().equals("undefined")
  }

  case class TestEvent( eventName : String )

  def start( event : String ) : Unit = {


    //TODO : determine if and what we're doing for context
    val context = Json.obj( "testing" -> "context" )

    /*
    NOTE:
    When we deploy a verticle we use require.noCache as each verticle instance must have the module evaluated.
    Also we run verticles in JS strict mode (with "use strict") -this means they cannot declare globals
    and other restrictions. We do this for isolation.
    However when doing a normal 'require' from inside a verticle we do not use strict mode as many JavaScript
    modules are written poorly and would fail to run otherwise.
     */
    exports = engine.eval("require.noCache('" + verticleName + "', null, true);").asInstanceOf[ScriptObjectMirror]
    if (functionExists( startFunctionName )) {
      val returnVal : String = exports.callMember( startFunctionName, event, context ).asInstanceOf[String]
      println( "Return Value : " + returnVal )
      //TODO : figure out this mechanism
      //startFuture.complete()
    }
    else {
      ???
    }
  }

  def stop() : Unit = {
    //TODO : I don't know that we want to support this, it's not something that amazon does, but we need to kill ourselves.
      ???
  }
}
