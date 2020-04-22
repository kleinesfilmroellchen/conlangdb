package klfr.conlangdb.http.util;

import java.net.HttpURLConnection;

/**
 * Enum for HTTP status codes and their standard messages.
 */
public enum HttpStatusCode {

	/** 200 - Everything ok. */
	OK(HttpURLConnection.HTTP_OK, "OK"),
	/** 201 - Resource uploaded was created successfully. The "Location"-Header contains its location. */
	CREATED(HttpURLConnection.HTTP_CREATED, "Created"),
	/** 202 - The request was recieved and is processed later. */
	ACCEPTED(HttpURLConnection.HTTP_ACCEPTED, "Accepted"),
	/** 204 - The request was processed and the response is intentionally empty. */
	NO_CONTENT(HttpURLConnection.HTTP_NO_CONTENT, "No Content"),
	/** 400 - Request contains syntactical or semantical errors. */
	BAD_REQUEST(HttpURLConnection.HTTP_BAD_REQUEST, "Bad Request"),
	/** 404 - The resource was not found. */
	NOT_FOUND(HttpURLConnection.HTTP_NOT_FOUND, "Not Found"),
	/** 405 - The HTTP method was incorrect, only other method(s) are allowed. */
	METHOD_UNALLOWED(HttpURLConnection.HTTP_BAD_METHOD, "Method Not Allowed"),
	/** 500 - Server error. */
	INTERNAL_SERVER_ERROR(HttpURLConnection.HTTP_INTERNAL_ERROR, "Internal Server Error"),
	/** 501 - Not implemented, this is rarely used. */
	NOT_IMPLEMENTED(HttpURLConnection.HTTP_NOT_IMPLEMENTED, "Not Implemented")

	;

	public final int code;
	public final String standardMessage;

	private HttpStatusCode(int code, String standardMessage) {
		this.code = code;
		this.standardMessage = standardMessage;
	}

	public String toString() {
		return String.format("%s - %s", code, standardMessage);
	}
	
}