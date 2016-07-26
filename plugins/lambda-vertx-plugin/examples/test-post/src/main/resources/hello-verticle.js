//------------------------------------------
// Lifecycle Post Policy
//
// This is an example of a lifecycle policy that is called
// after the completion of a resource lifecycle.  This could
// be after a creation type lifecycle or even a deletion
// lifecycle.
//
//------------------------------------------


var vertx       = require('vertx');                   //this is the container for all of built in functionality
var container   = require( 'vertx/container' )        //this is our context
var console     = require('vertx/console');
var eb          = vertx.eventBus;                     //the vertx event bus that we'll use to communicate with the framework

//the deployment has passed us in some config that will be in the container context
var config = container.config
console.log( "config is " + JSON.stringify( config ) )

//this is configured when we register the policy with the vertx framework
var policyConfig = config.config;
console.log( "policy config is " + JSON.stringify( policyConfig ) )

//gross date handling because we need a specific format for the message data structure : yyyy-MM-dd
var d = new Date(),
    month = '' + (d.getMonth() + 1),
    day = '' + d.getDate(),
    year = d.getFullYear();

if (month.length < 2) month = '0' + month;
if (day.length < 2) day = '0' + day;

var thisDay = [year, month, day].join('-');

var task = config.task
var detail = task.detail

//build the message structure that we're going to use for the calling
var message = {};
message.messageId = "GARBAGE";
message.source = "sms";
message.to = policyConfig.toNumber;
message.from = policyConfig.fromNumber;
message.date = thisDay;
message.subject = "Provisioning Complete";
message.body = "The requested resource has finished provisioning : " + detail.resource


//the vertx conversation topic "kafka" is mapped on the framework side to forward directly to the
//gestalt event bus.  Which means the payload needs to be in the GestaltEvent json format
var messageEvent = {};
messageEvent.event_name = "notifier.message.fromBus.event";
messageEvent.data = message;
eb.publish( "kafka", JSON.stringify( messageEvent ));

//the "module-return" vertx conversation address is configured by the framework to listen for the return value
//of a vertx module
var returnVal = {};
returnVal.id = config.id;
returnVal.status = "success";
returnVal.payload = "event sent : " + JSON.stringify( messageEvent );
eb.publish( "module-return", JSON.stringify( returnVal ));
