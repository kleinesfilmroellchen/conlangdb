package klfr.conlangdb.http;

/**
 * Enum for HTTP status codes and their standard messages.
 */
public enum HttpStatusCode {

	/** 200 - Everything ok. */
	OK(200, "OK"),
	/** 202 - The request was recieved and is processed later. */
	ACCEPTED(202, "Accepted"),
	/** 404 - The resource was not found. */
	NOT_FOUND(404, "Not Found"),
	/** 405 - The HTTP method was incorrect, only other method(s) are allowed. */
	METHOD_UNALLOWED(405, "Method Not Allowed");


	public final int code;
	public final String standardMessage;

	private HttpStatusCode(int code, String standardMessage) {
		this.code = code;
		this.standardMessage = standardMessage;
	}
	
}