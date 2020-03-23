package klfr.conlangdb.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import org.takes.*;
import org.takes.rs.*;

import klfr.conlangdb.CObject;

/**
 * Response wrapper for the ConlangDB server responses. This composes a couple
 * of responses into a single decorator.
 */
public class RsCWrap extends CObject implements Response {
	private static final Logger log = Logger.getLogger(RsCWrap.class.getCanonicalName());

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
		// BUG: The content-length header set previously by decorators causes most browsers to quit reading from the connection,
		// which leads to malformed websites being transmitted. It is best to manually delete the header first.
		this.res = new RsWithoutHeader(new RsWithStatus(new RsWithHeader(res, "Server", "ConlangDB/0.1"), status.code,
				status.standardMessage), "Content-Length");
	}

	public InputStream body() throws IOException {
		log.fine("body access in wrapper");
		return res.body();
	}

	public Iterable<String> head() throws IOException {
		return res.head();
	}

	@Override
	public CObject clone() {
		return new RsCWrap(res);
	}

}