package controllers

import java.util.UUID

import com.galacticfog.gestalt.lambda.io.domain.{LambdaDao, LambdaContentType}
import com.galacticfog.gestalt.lambda.{Global, LambdaFramework}
import com.galacticfog.gestalt.meta.api.authorization.Authorization
import com.galacticfog.gestalt.security.play.silhouette.{GestaltAuthProvider, GestaltFrameworkSecuredController}
import com.galacticfog.gestalt.security.api._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.{DummyAuthenticatorService, DummyAuthenticator}
import play.api._
import play.api.http.{HeaderNames, ContentTypeOf, Writeable}
import play.api.{Logger=>log}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.twirl.api.{JavaScript, Html}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}

object Application extends GestaltFrameworkSecuredController[DummyAuthenticator] {

  override def getAuthenticator: AuthenticatorService[DummyAuthenticator] = new DummyAuthenticatorService

  val lambdaFramework = LambdaFramework.getFramework()

  def unpackResponse[A](t : Try[A] )(implicit writeable : Writeable[A], contentTypeOf : ContentTypeOf[A]) : Result = {
    t match {
      case Success( s ) => Ok( s )
      case Failure( ex ) => {
        ex.printStackTrace
        BadRequest( Json.obj(
          "status" -> "error",
          "message" -> ex.getMessage( )
        ) )
      }
    }
  }

  def AsyncAuthz[B]( resource : String, action : String, request : SecuredRequest[B] )(code : => Future[Result]) : Future[Result] = {
    val authResponse = Authorization.isAuthorized( UUID.fromString( resource ), request.identity.account.id, action )
    authResponse match {
      case Success(resp) => {
        code
      }
      case Failure(ex) => {
        Future {
          ex.printStackTrace
          BadRequest( Json.obj(
            "status" -> "error",
            "message" -> ex.getMessage( )
          ) )
        }
      }
    }
  }

  def Authz[B]( resource : String, action : String, request : SecuredRequest[B] )(code : => Result) : Result = {
    val authResponse = Authorization.isAuthorized( UUID.fromString( resource ), request.identity.account.id, action )
    authResponse match {
      case Success(resp) => {
        code
      }
      case Failure(ex) => {
        ex.printStackTrace
        BadRequest( Json.obj(
          "status" -> "error",
          "message" -> ex.getMessage( )
        ) )
      }
    }

  }
  def index = GestaltFrameworkAuthAction(Option.empty[String]) {
    Ok(views.html.index("Your new application is ready."))
  }

  def getHealth = Action {
    Ok("healthy")
  }

  def createLambda = GestaltFrameworkAuthAction(Option.empty[String]).async(parse.json) { request =>
    log.trace( s"createLambda()")
    Future {
      unpackResponse(
        lambdaFramework.createLambda( request.body )
      )
    }
  }

  def updateLambda( id : String ) = GestaltFrameworkAuthAction(Option.empty[String]).async(parse.json) { request =>
    log.trace( s"updateLambda( $id )")
    AsyncAuthz( id, "lambda.update", request ) {
      Future {
        unpackResponse(
          lambdaFramework.updateLambda( id, request.body )
        )
      }
    }
  }

  def invokeLambda( id : String ) = Action.async(parse.json) { request =>
    log.trace( s"invokeLambda( $id )")
    Future {
      //@HACK - this is going to change when we're doing auth at the edge,  this is so wasteful and gross I can't even...
      lambdaFramework.getLambda( id ) match {
        case Success( s ) => {
          val lambda = s.validate[LambdaDao].get
          if ( lambda.public.isDefined && lambda.public.get ) {
            lambdaFramework.invokeLambda( id, request.body, Some( GestaltAPICredentials.getCredentials( request.headers.get( HeaderNames.AUTHORIZATION ).get ).get.headerValue ) ) match {
              case Success( s ) => Ok( s )
              case Failure( ex ) => {
                ex.printStackTrace
                BadRequest( Json.obj(
                  "status" -> "error",
                  "message" -> ex.getMessage( )
                ) )
              }
            }
          }
          else {
            val resp = authProvider.gestaltAuthImpl( request )
            val result = Await.result( resp, 10 seconds )
            result match {
              case Some( result ) => {
                lambdaFramework.invokeLambda( id, request.body, Some( GestaltAPICredentials.getCredentials( request.headers.get( HeaderNames.AUTHORIZATION ).get ).get.headerValue ) ) match {
                  case Success( s ) => Ok( s )
                  case Failure( ex ) => {
                    ex.printStackTrace
                    BadRequest( Json.obj(
                      "status" -> "error",
                      "message" -> ex.getMessage( )
                    ) )
                  }
                }
              }
              case None => {
                notAuthenticated
              }
            }
          }
        }
        case Failure( ex ) => {
          ex.printStackTrace
          BadRequest( Json.obj(
            "status" -> "error",
            "message" -> ex.getMessage( )
          ) )
        }
      }
    }
  }

  def invokeLambdaSync( id : String ) = Action(parse.json) { request =>
    log.trace( s"invokeLambdaSync( $id )")
    invokeSync( id, request.body, request )
  }

  def invokeLambdaSyncNoBody( id : String ) = Action { request =>
      log.trace( s"invokeLambdaSyncNoBody( $id )" )

      //TODO : this would have to be replumbed all the way down to the executor to remove the need for this fake data.  Consider it....
      val fakeEvent = Json.obj(
        "eventName" -> UUID.randomUUID.toString,
        "data" -> Json.obj( )
      )

      invokeSync( id, fakeEvent, request )
  }

  def invokeSync[B]( id : String, body : JsValue, request : Request[B] ) = {

    //@HACK - this is going to change when we're doing auth at the edge,  this is so wasteful and gross I can't even...
    lambdaFramework.getLambda( id ) match {
      case Success( s ) => {
        val lambda = s.validate[LambdaDao].get
        if( lambda.public.isDefined && lambda.public.get )
        {
          invokeImpl( id, body, None )
        }
        else {
          val resp = authProvider.gestaltAuthImpl( request )
          val result = Await.result( resp, 10 seconds )
          result match {
            case Some( result ) => {
              invokeImpl( id, body, Some(GestaltAPICredentials.getCredentials( request.headers.get(HeaderNames.AUTHORIZATION).get ).get.headerValue) )
            }
            case None => {
              notAuthenticated
            }
          }
        }
      }
      case Failure( ex ) => {
        ex.printStackTrace
        BadRequest( Json.obj(
          "status" -> "error",
          "message" -> ex.getMessage( )
        ) )
      }
    }
  }

  def invokeImpl( id : String, body : JsValue, creds : Option[String] ) = {

    lambdaFramework.invokeLambdaSync( id, body, creds ) match {
      case Success( s ) => {
        s.contentType match {

          //TODO : fix content typing when it's supported in the meta SDK
          //case LambdaContentType.TEXT => Ok( s.result )
          //case LambdaContentType.HTML => Ok( Html( s.result ) )
          //case LambdaContentType.JS => Ok( JavaScript( s.result ) )

          case _ => Ok( Html( s.result ) )
        }
      }
      case Failure( ex ) => {
        ex.printStackTrace
        BadRequest( Json.obj(
          "status" -> "error",
          "message" -> ex.getMessage( )
        ) )
      }
    }
  }

  def deleteLambda( id : String ) = GestaltFrameworkAuthAction(Option.empty[String]).async { request =>
    log.trace( s"deleteLambda($id)")
    AsyncAuthz( id, "lambda.delete", request ) {
      Future {
        unpackResponse(
          lambdaFramework.deleteLambda( id )
        )
      }
    }
  }

  def invalidateCache( id : String ) = GestaltFrameworkAuthAction(Option.empty[String]).async { request =>
    log.trace( s"invalidateCache($id)")
    AsyncAuthz( id, "lambda.update", request ) {
      Future {
        unpackResponse(
          lambdaFramework.invalidateCache( id )
        )
      }
    }
  }

  def getResult( id : String ) = GestaltFrameworkAuthAction(Option.empty[String]).async { request =>
    log.trace( s"getResult($id)")
    AsyncAuthz( id, "lambda.invoke", request ) {
      Future {
        unpackResponse(
          lambdaFramework.getResult( id )
        )
      }
    }
  }


  def getLambda( id : String ) = GestaltFrameworkAuthAction(Option.empty[String]).async { request =>
    log.trace( s"getLambda($id)")
    AsyncAuthz( id, "lambda.read", request ) {
      Future {
        unpackResponse(
          lambdaFramework.getLambda( id )
        )
      }
    }
  }

  def searchLambdas = GestaltFrameworkAuthAction(Option.empty[String]).async { request =>
    log.trace( s"searchLambdas")
    Future {
      unpackResponse(
        lambdaFramework.searchLambdas( request.queryString )
      )
    }
  }
}
