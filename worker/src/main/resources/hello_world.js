exports.hello = function(event, context) {
	var parsedEvent = JSON.parse( event )
	// Call the console.log function.
	console.log("Hello World");
	console.log( "Parsed Event : " + JSON.stringify( parsedEvent ) );

	return JSON.stringify( parsedEvent );
}
