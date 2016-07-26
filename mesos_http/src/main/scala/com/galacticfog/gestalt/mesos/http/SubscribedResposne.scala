package com.galacticfog.gestalt.lambda.impl.http

case class Subscribed( framework_id : FrameworkID )
case class SubscribedResponseEnvelope( subscribed : Subscribed, `type` : String )
