package klfr.conlangdb.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHeaders;
import org.takes.rs.RsWithHeader;
import org.takes.rs.RsWithType;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;

/**
 * Wrapper for adding standard HTML head with frontend scripting as well as body
 * headers and footers to any page of the webapp.
 */
public class TkStaticPageWrap extends CObject implements Take {
	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(TkStaticPageWrap.class.getCanonicalName());

	private final Take sub;
	private final String pagename;

	public TkStaticPageWrap(Take sub, String pagename) {
		this.sub = sub;
		this.pagename = pagename;
	}

	@Override
	public Response act(Request req) {
		try {
			Response prepared = sub.act(req);
			log.fine("response " + prepared.toString());
			String language = Arrays.asList(
					new RqHeaders.Smart(req).header("Accept-Language").stream().findFirst().orElse("en").split("\\,"))
					.stream().map(s -> s.split("\\;")[0]).findFirst().orElse("en");
			log.fine("language " + language);
			var w = new StringWriter();
			new InputStreamReader(sequencify(streamify("<!DOCTYPE html>\n<html>                     "),
					CResources.openBinary("html/head.html").get(),
					streamify("<script>SETTINGS.pagename = \"" + pagename + "\";</script></head><body>"),
					CResources.openBinary("html/header.html").get(), prepared.body(), streamify("</body></html>")))
							.transferTo(w);
			var body = w.toString();
			log.fine(body);
			return new RsWithHeader(new RsWithType(new RsCWrap(new Response() {
				// COMBAK: set content-length header by pre-generating the body on act() call
				public Iterable<String> head() throws IOException {
					return prepared.head();
				}

				public InputStream body() throws IOException {
					try {
						return streamify(body);
					} catch (Throwable t) {
						log.log(Level.SEVERE, () -> f("exception %s%n%s", t,
								String.join("\n", Arrays.asList(t.getStackTrace().toString()))));
						throw t;
					}
				}
			}), "text/html", Charset.forName("utf-8")), "Content-Length",
					Integer.toString(body.getBytes(Charset.forName("utf-8")).length));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Makes a UTF-8 encoded input stream from the string.
	 */
	public static InputStream streamify(final String s) {
		return new ByteArrayInputStream(s.getBytes(Charset.forName("utf-8")));
	}

	public static InputStream sequencify(final InputStream... streams) {
		return new SequenceInputStream(new Enumeration<InputStream>() {
			private int i = 0;

			@Override
			public boolean hasMoreElements() {
				return i < streams.length;
			}

			@Override
			public InputStream nextElement() {
				return streams[i++];
			}
		});
	}

	@Override
	public CObject clone() {
		return new TkStaticPageWrap(sub, pagename);
	}
}