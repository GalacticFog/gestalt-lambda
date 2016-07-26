package com.galacticfog.gestalt.lambda.impl

import java.io.File

import java.io.IOException
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util.Iterator
import java.util.ServiceLoader

case class ClassPathExistsException( msg : String ) extends Exception

object DynLoader {

  def extendClasspath( dir : File, inLoader : ClassLoader ) = {
    try {

      val sysLoader : URLClassLoader = inLoader match {
        case u : URLClassLoader => u
        case _ => throw new Exception( "Cast Exception" )
      }

      val urls : Seq[URL] = sysLoader.getURLs()
      val udir = dir.toURI().toURL()

      val udirs = udir.toString()
      urls.find( u => u.toString().equalsIgnoreCase(udirs) ) match {
        case Some(s) => throw new ClassPathExistsException( "class path exists" )
        case None => {}
      }

      val sysClass = classOf[URLClassLoader]

      val method : Method = sysClass.getDeclaredMethod("addURL", classOf[URL] )
      method.setAccessible(true)
      val udirObj = udir match {
        case o : Object => o
        case _ => throw new Exception( "impossible" )
      }

      method.invoke(sysLoader, udirObj )
      println( "Loaded " + udirs + " dynamically...")
    }
    catch {
      case cpe : ClassPathExistsException => {
        println( "class path exists, ignoring" )
      }
      case t : Throwable  => {
        t.printStackTrace();
      }
    }
  }
}
