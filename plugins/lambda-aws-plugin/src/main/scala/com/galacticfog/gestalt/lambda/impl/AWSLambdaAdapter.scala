package com.galacticfog.gestalt.lambda.impl

import java.util.Base64

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model._
import com.galacticfog.gestalt.Gestalt
import com.galacticfog.gestalt.lambda.io.domain.{LambdaDao, LambdaEvent}
import com.galacticfog.gestalt.lambda.plugin.LambdaAdapter
import play.api.libs.json.{JsError, JsSuccess, Json}

case class AWSLambdaInfo( s3Bucket : String, s3Name : String, description : Option[String], functionName : String,
                          handler : String, memorySize : Int, publish : Boolean, role : String, runtime : String,
                          timeoutSecs : Option[Int] );

object AWSLambdaInfo {
  implicit val lambdaInfoFormat = Json.format[AWSLambdaInfo]
}

class AWSLambdaAdapter() extends LambdaAdapter {

  val client = getClient()

  def getClient() : AWSLambdaClient = {

    //TODO : fix when config secrets are solved
    val publicKey = sys.env.get( "AWS_PUBLIC" ) getOrElse {
      throw new Exception( "AWS_PUBLIC environment variable not set for AWS adapter" )
    }
    val privateKey = sys.env.get( "AWS_PRIVATE" ) getOrElse {
      throw new Exception( "AWS_PRIVATE environment variable not set for AWS adapter" )
    }

    val awsCredentials = new BasicAWSCredentials( publicKey, privateKey )
    new AWSLambdaClient( awsCredentials )
  }

  def getPluginName : String  = "AWSLambdaAdapter"

  def createLambda( data : LambdaDao ) : String = {
    //Logger.debug("AWSLambdaAdapter::createLambda")


    val request : CreateFunctionRequest = new CreateFunctionRequest()
    val code : FunctionCode = new FunctionCode()

    val lambdaInfo = data.artifactDescription.validate[AWSLambdaInfo] match {
      case JsSuccess( s, _ ) => s
      case err@JsError(_) => {
        throw new Exception( "Error parsing lambda artifact info : " + Json.toJson( JsError.toFlatJson(err) ) )
      }
    }

    //TODO : should we allow you to pass the code zip here and pass it up to amazon?  Or just specify the bucket and name?

    code.setS3Bucket( lambdaInfo.s3Bucket )
    code.setS3Key( lambdaInfo.s3Name )

    request.setCode( code )
    if( lambdaInfo.description.isDefined ) request.setDescription( lambdaInfo.description.get )
    request.setFunctionName( lambdaInfo.functionName )
    request.setHandler( lambdaInfo.handler )
    request.setMemorySize( lambdaInfo.memorySize )
    request.setPublish( lambdaInfo.publish )
    request.setRole( lambdaInfo.role )
    request.setRuntime( lambdaInfo.runtime )
    val secs : Integer = Int.box( lambdaInfo.timeoutSecs getOrElse 3 )
    request.setTimeout( secs )

    val client = getClient()
    val result : CreateFunctionResult = client.createFunction( request )

    //Logger.debug( "created arn : " + result.getFunctionArn )
    //Logger.debug( "created name : " + result.getFunctionName )

    //this is how we'll reference it form here on
    result.getFunctionName
  }

  def deleteLambda( data : LambdaDao ) : Unit = {
    //Logger.debug("AWSLambdaAdapter::deleteLambda")

    val lambda = data.artifactDescription.validate[AWSLambdaInfo] match {
      case JsSuccess( s, _ ) => s
      case err@JsError(_) => {
        throw new Exception( "Error parsing lambda event : " + Json.toJson( JsError.toFlatJson(err) ) )
      }
    }

    val client = getClient()

    val request : DeleteFunctionRequest = new DeleteFunctionRequest()
    request.setFunctionName( data.payload.get )

    client.deleteFunction( request )
  }

  def invokeLambda( data : LambdaDao, event : LambdaEvent ) : String = {
    //Logger.debug("AWSLambdaAdapter::invokeLambda")

    val request : InvokeRequest = new InvokeRequest()
    request.setFunctionName( data.payload.get )
    request.setLogType( LogType.Tail )
    request.setInvocationType( InvocationType.RequestResponse )

    val payload = event.data.toString
    request.setPayload( payload )

    val client = getClient()
    val response : InvokeResult = client.invoke( request )

    val logString = new String(Base64.getDecoder.decode( response.getLogResult.getBytes ))

    //Logger.debug( "invoke result : " + response.toString )
    //Logger.debug( "log string : " + logString )

    //TODO : consider returning a complex type from this function so that we can store the log results as well???

    new String( response.getPayload.array() )
  }


}
