package klfr.conlangdb.handlers;

import klfr.conlangdb.CObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Logger;

import com.sun.net.httpserver.*;

/**
 * Superclass of all handlers.
 */
public abstract class GeneralHandler extends CObject implements HttpHandler {
	protected static final long serialVersionUID = 1L;

	public static final long CACHE_SECONDS = 15L;

	public Writer bodyWriter;

	protected static final Logger log = Logger.getLogger(GeneralHandler.class.getCanonicalName());

	@Override
	public void handle(HttpExchange exchange) {
		try {
			log.info(() -> f("%s request from %s on /", exchange.getRequestMethod(), exchange.getRemoteAddress()));
			var headers = exchange.getRequestHeaders();
			var modified = headers.getFirst("If-Modified-Since");
			if (modified != null) {
				try {
					Instant requestedTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(modified));
					if (requestedTime.isAfter(Instant.now().minusSeconds(CACHE_SECONDS))) {
						// user cache is still less than CACHE_SECONDS old, send 304
						exchange.sendResponseHeaders(304, -1);
						exchange.close();
						return;
					}
				} catch (DateTimeParseException e) {
					exchange.sendResponseHeaders(400, -1);
					exchange.close();
					return;
				}
			}
			// only cache for 15 seconds; should be changed by statically served files
			exchange.getResponseHeaders().add("Cache-Control", "max-age=" + CACHE_SECONDS);
			exchange.getResponseHeaders().add("Server", "ConlangDB");
			exchange.getResponseHeaders().add("Set-Cookie",
					headers.getFirst("Cookie") == null ? "" : headers.getFirst("Cookie"));
			exchange.getResponseHeaders().add("Date",
					DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())));
			exchange.getResponseHeaders().add("Connection", "keep-alive");
			exchange.getResponseHeaders().add("Keep-Alive", "timeout=4, max=30");

			bodyWriter = new BufferedWriter(new OutputStreamWriter(exchange.getResponseBody(), "UTF-8"));

			final var method = exchange.getRequestMethod().trim().toUpperCase();
			var result = false;
			switch (method) {
				case "HEAD":
					result = this.doHead(exchange);
					break;
				case "GET":
					var hresult = this.doHead(exchange);
					if (!hresult)
						result = this.doGet(exchange);
					break;
				case "POST":
					result = this.doPost(exchange);
					break;
				case "PUT":
					result = this.doPut(exchange);
					break;
				case "DELETE":
					result = this.doDelete(exchange);
					break;
				default:
					exchange.sendResponseHeaders(405, -1);
					exchange.close();
					return;
			}
			// if implementor didn't close, close with 200
			if (!result) {
				exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : 0);
				while (exchange.getResponseCode() == -1) Thread.yield();
				bodyWriter.flush();
				exchange.getResponseBody().flush();
				exchange.close();
			}
		} catch (Throwable t) {
			log.severe(f("Server handler exception:%n%s", Arrays.stream(t.getStackTrace()).map(ste -> ste.toString()).reduce((a,b) -> a + System.lineSeparator() + b)));
		}
	}

	/**
	 * Called on GET request from the client, should NOT set any headers. The
	 * exchange can be closed and the closing noted to the caller.
	 * 
	 * @return whether the exchange was closed (true) or not (false).
	 * @param exchange
	 */
	protected abstract boolean doGet(HttpExchange exchange) throws IOException;

	/**
	 * Called on HEAD and GET request from the client, should set the GET headers.
	 * The exchange can be closed and the closing noted to the caller.
	 * 
	 * @return whether the exchange was closed (true) or not (false).
	 * @param exchange
	 */
	protected abstract boolean doHead(HttpExchange exchange) throws IOException;

	/**
	 * Called on POST request from the client. The exchange can be closed and the
	 * closing noted to the caller.
	 * 
	 * @return whether the exchange was closed (true) or not (false).
	 * @param exchange
	 */
	protected abstract boolean doPost(HttpExchange exchange) throws IOException;

	/**
	 * Called on PUT request from the client. The exchange can be closed and the
	 * closing noted to the caller.
	 * 
	 * @return whether the exchange was closed (true) or not (false).
	 * @param exchange
	 */
	protected abstract boolean doPut(HttpExchange exchange) throws IOException;

	/**
	 * Called on DELETE request from the client. The exchange can be closed and the
	 * closing noted to the caller.
	 * 
	 * @return whether the exchange was closed (true) or not (false).
	 * @param exchange
	 */
	protected abstract boolean doDelete(HttpExchange exchange) throws IOException;

	@Override
	public CObject clone() {
		return new MainPageHandler();
	}

}