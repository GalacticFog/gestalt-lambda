package com.galacticfog.gestalt.lambda.io

abstract class JdbcConnectionInfo(
    val subprotocol: String,
    val driver: String,
    val host: String, 
    val port: Option[Int], 
    val database: String, 
    val username: Option[String], 
    val password: Option[String],
    val args: String*) {
  
  private[io] val dbsep = "/"
  private[io] val protosep = "://"
  
  def url(args: (String,String)*) = {
    val portstr = if(port.isDefined) s":${port.get}" else ""
    "jdbc:%s%s%s%s%s%s%s".format(subprotocol, protosep, host, portstr, dbsep, database,
        if (!args.isEmpty) mkargs(args.toMap) else "")
  }
  
  private[io] def mkargs(args: Map[String,String]) = args map { 
    case (k,v) => "%s=%s".format(k, v) 
  } addString(new StringBuilder, ";", ";", "") toString
  
}

