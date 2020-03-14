package klfr.conlangdb.http;

import org.takes.*;
import org.takes.rs.*;
import org.takes.facets.fallback.*;
import org.takes.misc.Opt;

public class FbFail implements Fallback {

	public final HttpStatusCode code;

	public FbFail(HttpStatusCode code) {
		this.code = code;
	}

	public Opt<Response> route(RqFallback req) {
		if (code.code != req.code())
			return new Opt.Empty<Response>();
		else
			return new Opt.Single<Response>(new RsWithStatus(new RsText(code.standardMessage), code.code, code.standardMessage));
	}
}
