#JavaScript Executor

This is an implementation of a Mesos Executor that will execute a lambda written in JavaScript.  The current implementation requires that the lambda author build and publish their dependencies into a directory and zip *both* the executable and all of it's dependencies into an artifact to be inflated and executed. Or the author can provider inline code that is Base64 encoded in the payload.  Examples of both will be given.

We're currently using a docker container built from the `java:latest` and executing the Java 8 Nashorn JavaScript Engine.

Here is a brief example of how to create a JavaScript lambda.

#Hello World

Simplest possible JavaScript lambda.  Note that you must create a named function in both cases.  In the artifact case, you then create a zip file that contains your scripts, and specify an artifact URI in the payload upon creation.  Examples follow

### hello_world.js
```javascript
function hello(event, context) {
	// Call the console.log function.
	var parsed = JSON.parse( event );
	console.log("Hello World");
	console.log( "Event Name : " + parsed.eventName );
	return "SUCCESS";
};
```

Now while you're in the directory that contains your project you can run the following command to build an artifact for use

`zip -r hello_world.zip *`

Then you specify your lambda payload like so : 

### Artifact Example Payload
```json
{
	"eventFilter": "com.awesome.HelloWorld",
		"artifactDescription": {
			"artifactUri": "https://s3.amazonaws.com/my.lambdas/hello_world.zip",
			"description": "super simple lambda for javascript",
			"functionName": "hello",
			"handler": "hello_world.js",
			"memorySize": 1024,
			"cpus": 0.2,
			"publish": false,
			"role": "doesntgetused",
			"runtime": "dotnet",
			"timeoutSecs": 180
		}
}
```

### Inline Example Payload

```json
{
	"eventFilter": "com.awesome.HelloWorld",
		"artifactDescription": {
			"code": "ZnVuY3Rpb24gaGVsbG8oZXZlbnQsIGNvbnRleHQpIHsNCiAgLy8gQ2FsbCB0aGUgY29uc29sZS5sb2cgZnVuY3Rpb24uDQogIGNvbnNvbGUubG9nKCJIZWxsbyBXb3JsZCIpOw0KICBjb25zb2xlLmxvZyggIkV2ZW50IE5hbWUgOiAiICsgZXZlbnQuZXZlbnROYW1lICk7DQogIHJldHVybiAiU1VDQ0VTUyI7DQp9Ow==",
			"description": "super simple lambda for javascript",
			"functionName": "hello",
			"handler": "doesntgetused",
			"memorySize": 1024,
			"cpus": 0.2,
			"publish": false,
			"role": "doesntgetused",
			"runtime": "dotnet",
			"timeoutSecs": 180
		}
}
```
Once you have your payload, you simply `POST` to the `/lambdas` endpoint for the lambda service : 

`POST <hostname>/lambdas < payload.json`

And you can invoke it like so : 

`POST <hostname>/lambdas/{id}/invoke < event.payload.json`
