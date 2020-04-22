package klfr.conlangdb.http.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.takes.Response;
import org.takes.facets.fallback.Fallback;
import org.takes.facets.fallback.RqFallback;
import org.takes.misc.Opt;
import org.takes.rs.RsText;
import org.takes.rs.RsWithStatus;

import klfr.conlangdb.CObject;

/**
 * Fallback that handles a specific HTTP error code. It prints a fail message to
 * the logging system and returns a Response with the standard message, e.g.
 * "Not Found" or "Bad Request" in the HTTP reason and body.
 */
public class FbFail extends CObject implements Fallback {
	private static final long serialVersionUID = 1L;

	public final HttpStatusCode code;

	private static final Logger log = Logger.getLogger(FbFail.class.getCanonicalName());

	public FbFail(HttpStatusCode code) {
		this.code = code;
	}

	public Opt<Response> route(RqFallback req) {
		if (code.code != req.code())
			return new Opt.Empty<Response>();
		else {
			log.log(Level.WARNING, "Request %s exited with code %s".formatted(req, code), req.throwable());
			return new Opt.Single<Response>(
					new RsWithStatus(new RsText(code.standardMessage), code.code, code.standardMessage));
		}
	}

	public CObject clone() {
		return new FbFail(this.code);
	}
}
