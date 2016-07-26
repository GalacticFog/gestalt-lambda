package com.galacticfog.gestalt.lambda.impl.http

import com.galacticfog.gestalt.lambda.impl.http.InternalSchedulerDriver

import org.apache.mesos.Protos.Credential
import org.apache.mesos.Protos.FrameworkInfo
import org.apache.mesos.{Protos, Scheduler, SchedulerDriver}

class GestaltSchedulerDriver( scheduler : Scheduler, frameworkInfo : Protos.FrameworkInfo, master : String, implicitAcknowledgement : Boolean = true, credential : Credential = null ) extends
  InternalSchedulerDriver( scheduler, frameworkInfo, master, implicitAcknowledgement, credential ) with SchedulerDriver //with Closeable
{

}
