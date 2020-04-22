package klfr.conlangdb.http.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqRequestLine;
import org.takes.rq.RqSocket;

import klfr.conlangdb.CObject;

/**
 * Take decorator that logs all requests to the Java logging system.
 */
public class TkLog extends CObject implements Take {
	private static final long serialVersionUID = 1L;

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
			log.info("HTTP %6s %s from %s".formatted(rqline.method(), rqline.uri(), remote));
			return sub.act(socketrq);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception in Take.", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public CObject clone() {
		return new TkLog(sub);
	}
}
