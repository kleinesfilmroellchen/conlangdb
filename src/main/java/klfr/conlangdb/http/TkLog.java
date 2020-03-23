package klfr.conlangdb.http;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.takes.*;
import org.takes.rs.*;

import klfr.conlangdb.CObject;

import org.takes.rq.*;

/**
 * Take decorator that logs all requests to the Java logging system.
 */
public class TkLog extends CObject implements Take {

	private static final Logger log = Logger.getLogger(TkLog.class.getPackageName());

	private final Take sub;

	public TkLog(Take sub) {
		this.sub = sub;
	}

	public Response act(final Request req) {
		try {
			var rqline = new RqRequestLine.Base(req);
			var socketrq = new RqSocket(rqline);
			var remote = socketrq.getRemoteAddress();
			log.info("%6s %s from %s".formatted(rqline.method(), rqline.uri(), remote));
			return sub.act(socketrq);
		} catch (Exception e) {
			log.severe(() -> String.format("Exception in Take: %s%n%s", e.getLocalizedMessage(), String.join(
					System.lineSeparator(),
					Arrays.asList(e.getStackTrace()).stream().map(x -> x.toString()).collect(Collectors.toList()))));
			throw new RuntimeException(e);
		}
	}

	@Override
	public CObject clone() {
		return new TkLog(sub);
	}
}
