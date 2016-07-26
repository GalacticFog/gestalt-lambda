package com.galacticfog.lambda.executor

import java.net.URL
import java.util.{Base64, Scanner}
import javax.script.{Invocable, ScriptEngine, ScriptEngineManager, ScriptException}
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

import jdk.nashorn.api.scripting.ScriptObjectMirror

import scala.io.Source

class JSWorker( verticleName : String, startFunctionName : String ) {

  val log = LoggerFactory.getLogger( JSWorker.this.getClass )

  private val JVM_NPM = "npm-js.js"

  var engine : ScriptEngine = null
  var exports : ScriptObjectMirror = null

  def init() : ScriptEngine = {
    val mgr : ScriptEngineManager = new ScriptEngineManager(null)
    engine = mgr.getEngineByName("nashorn")

    if( Thread.currentThread.getContextClassLoader == null )
    {
      //log.debug( "NO CONTEXT CLASSLOADER" )
      throw new Exception( "No context classloader set, this will cause classpath problems." )
    }

    //@debug classpath below
    /*
    else
    {
      log.debug( "CLASSLOADER GTG : " )

      def urlses(cl: ClassLoader): Array[java.net.URL] = cl match {
        case null => Array()
        case u: java.net.URLClassLoader => u.getURLs() ++ urlses(cl.getParent)
        case _ => urlses(cl.getParent)
      }

      val  urls = urlses(Thread.currentThread.getContextClassLoader)
      log.debug(urls.filterNot(_.toString.contains("ivy")).mkString("\n"))
    }
    */


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
        e.printStackTrace
        throw new IllegalStateException("Failed to eval: " + e.getMessage(), e)
    }

    engine
  }

  def functionExists(functionName : String) : Boolean = {
    val som : Object = exports.getMember(functionName)
    som != null && !som.toString().equals("undefined")
  }

  case class TestEvent( eventName : String )

  def run( event : String, code : String, context : String ) : String = {

    //TODO : determine if and what we're doing for context
    val bInline = code.length > 0

    val rawCode = if( !bInline ) {
      Source.fromFile(verticleName).getLines.mkString("\n")
    } else {
      new String( Base64.getDecoder.decode( code.getBytes ) )
    }

    engine.eval( rawCode ).asInstanceOf[ScriptObjectMirror]
    val invoker = engine.asInstanceOf[Invocable]
    val returnVal = invoker.invokeFunction( startFunctionName, event, context ).asInstanceOf[String]
    log.debug( "Return Value : " + returnVal )
    returnVal
  }

  def stop() : Unit = {
    //TODO : I don't know that we want to support this, it's not something that amazon does, but we need to kill ourselves.
      ???
  }
}
