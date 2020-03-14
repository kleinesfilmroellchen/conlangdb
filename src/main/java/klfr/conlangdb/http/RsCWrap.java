package klfr.conlangdb.http;

import java.io.IOException;
import java.io.InputStream;

import org.takes.*;
import org.takes.rs.*;

/**
 * Response wrapper for the ConlangDB server responses. This composes a couple of responses into a single decorator.
 */
public class RsCWrap implements Response {

	private final Response res;

	/**
	 * Wraps the response in a simple "success" response code header
	 */
	public RsCWrap(Response res) {
		this(res, HttpStatusCode.OK);
	}

	/**
	 * Uses given response code and the default reason message. Useful for errors.
	 */
	public RsCWrap(Response res, HttpStatusCode status) {
		this.res = new RsWithStatus(new RsWithHeader(new RsWrap(res), "Server", "ConlangDB/0.1"), status.code, status.standardMessage);
	}

	public InputStream body() throws IOException {
		return res.body();
	}
	public Iterable<String> head() throws IOException {
		return res.head();
	}

	
}