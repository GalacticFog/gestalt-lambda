#.NET Executor

This is an implementation of a Mesos Executor that will execute a lambda written for the .NET Core CLR.  The current implementation requires that the lambda author build and publish their dependencies into a directory and zip *both* the executable and all of it's dependencies into an artifact to be inflated and executed.

We're currently using a docker container built from the `ubuntu:trusty`, but more specifically we're using the following base : `buildpack-deps:trusty-scm`.  

In order to remaing consistent, lambda authors should use the following container to build and publish their executables : `microsoft/dotnet:latest` which is also based on the above listed docker images.

Here is a brief example of how to create a .NET lambda.

#Hello World

Simplest possible .NET lambda.  Note that you must implement `Main(string[] args)` entry point in order for your lambda to be executed by the system.

### Program.cs
```c#
using System;

namespace ConsoleApplication
{
	public class Program
	{
		public static void Main(string[] args)
		{
			Console.WriteLine("Hello World!");
		}
	}
}
```

### project.json

```json
{
	"version": "1.0.0-*",
		"compilationOptions": {
			"emitEntryPoint": true
		},

		"dependencies": {
			"Microsoft.NETCore.Runtime": "1.0.1-beta-*",
			"System.IO": "4.0.11-beta-*",
			"System.Console": "4.0.0-beta-*",
			"System.Runtime": "4.0.21-beta-*"
		},

		"frameworks": {
			"dnxcore50": { }
		}
}
```

Now while you're in the directory that contains your project you can run the following command to build and publish your lambda :

`>docker run --rm -v "$PWD":/dotnet_call -w /dotnet_call microsoft/dotnet@sha256:19ab67ce4fc80a1c1c68c45961216805c2119336e51be2132d4b2487a6a7034b /bin/bash -c "dotnet restore && dotnet build && dotnet publish"`

The above command will mount your working directory as a writeable volume in the docker container, and then execute the commands necessary to pull down all the dependencies specified in your project, compild your code, and publish the executable and the dependencies in a directory for the specific runtime flavor (in this case `ubuntu:trust`)

You can then zip up the contents of your directory : `zip -r hello_world.zip *`

Then you specify your lambda payload like so : 

```json
{
	"eventFilter": "com.awesome.HelloWorld",
		"artifactDescription": {
			"artifactUri": "https://s3.amazonaws.com/my.lambdas/hello_world.zip",
			"description": "super simple lambda for dotnet",
			"functionName": "doesntgetused",
			"handler": "bin/Debug/dnxcore50/ubuntu.14.04-x64/hello_world",
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


