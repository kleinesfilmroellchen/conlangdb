package klfr.conlangdb.http.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
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

	private static final Logger log = Logger.getLogger(TkLog.class.getCanonicalName());

	private static final DateTimeFormatter perfTimeFormat = new DateTimeFormatterBuilder().optionalStart()
			.optionalStart().optionalStart().appendValue(ChronoField.SECOND_OF_MINUTE).appendLiteral("s ").optionalEnd()
			.appendValue(ChronoField.MILLI_OF_SECOND).appendLiteral("ms / ").optionalEnd()
			.appendValue(ChronoField.NANO_OF_SECOND).appendLiteral("ns ").optionalEnd().toFormatter();

	private final Take sub;

	public TkLog(final Take sub) {
		this.sub = sub;
	}

	public Response act(final Request req) {
		try {
			final var before = Instant.now();
			final var rqline = new RqRequestLine.Base(req);
			final var socketrq = new RqSocket(rqline);
			final var remote = socketrq.getRemoteAddress();
			log.info("HTTP %6s %s from %s".formatted(rqline.method(), rqline.uri(), remote));

			final var res = sub.act(socketrq);

			log.info("PERFORMANCE %6s %s took %s".formatted(rqline.method(), rqline.uri(),
					perfTimeFormat.format(Duration.between(before, Instant.now()).addTo(LocalTime.of(0, 0)))));

			return res;
		} catch (Exception e) {
			log.log(Level.SEVERE, "EXCEPTION in Take.", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public CObject clone() {
		return new TkLog(sub);
	}
}
