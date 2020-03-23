package klfr.conlangdb.http;

import java.util.logging.Logger;

import org.takes.*;
import org.takes.rs.*;

import klfr.conlangdb.CObject;

import org.takes.facets.fallback.*;
import org.takes.misc.Opt;

public class FbFail extends CObject implements Fallback {

	public final HttpStatusCode code;

	private static final Logger log = Logger.getLogger(FbFail.class.getCanonicalName());

	public FbFail(HttpStatusCode code) {
		this.code = code;
	}

	public Opt<Response> route(RqFallback req) {
		if (code.code != req.code())
			return new Opt.Empty<Response>();
		else {
			log.warning("%s Request exited with code %s".formatted(req, code));
			return new Opt.Single<Response>(
					new RsWithStatus(new RsText(code.standardMessage), code.code, code.standardMessage));
		}
	}

	public CObject clone() {
		return new FbFail(this.code);
	}
}
