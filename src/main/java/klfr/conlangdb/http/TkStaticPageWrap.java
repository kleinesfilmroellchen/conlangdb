package klfr.conlangdb.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHeaders;
import org.takes.rs.RsGzip;;
import org.takes.rs.RsWithHeaders;
import org.takes.rs.RsWithType;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import static klfr.conlangdb.http.RsUnicodeText.streamify;

/**
 * Wrapper for adding standard HTML head with frontend scripting as well as body
 * headers and footers to any page of the webapp.
 */
public class TkStaticPageWrap extends CObject implements Take {
	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(TkStaticPageWrap.class.getCanonicalName());

	private final Take sub;
	private final Optional<Take> headsub;
	private final String pagename;

	public TkStaticPageWrap(Take sub, String pagename) {
		this.sub = sub;
		this.pagename = pagename;
		this.headsub = Nothing();
	}

	/**
	 * This static page wrapper constructor accepts a second take providing HTML
	 * head contents and more HTTP headers, if necessary.
	 * 
	 * @param sub      The primary take that provides a response.
	 * @param headsub  The head take. Its response body is put into the HTML header
	 *                 and its HTTP headers are also included.
	 * @param pagename ID of the page, used to provide page title and help texts.
	 */
	public TkStaticPageWrap(Take sub, Take headsub, String pagename) {
		this.sub = sub;
		this.pagename = pagename;
		this.headsub = Just(headsub);
	}

	@Override
	public Response act(Request req) {
		try {
			Response prepared = sub.act(req);
			log.fine("response " + prepared.toString());
			Response headPrepared = headsub.isPresent() ? headsub.get().act(req) : new Response() {

				@Override
				public Iterable<String> head() throws IOException {
					return List.of();
				}

				@Override
				public InputStream body() throws IOException {
					return InputStream.nullInputStream();
				}

			};

			String language = Arrays.asList(
					new RqHeaders.Smart(req).header("Accept-Language").stream().findFirst().orElse("en").split("\\,"))
					.stream().map(s -> s.split("\\;")[0]).findFirst().orElse("en");
			log.fine("language " + language);

			var w = new StringWriter();
			new InputStreamReader(
					sequencify(streamify("<!DOCTYPE html>\n<html>"), CResources.openBinary("html/head.html").get(),
							streamify("<script>SETTINGS.pagename = \"" + pagename + "\";</script></head>\n"),
							headPrepared.body(), streamify("<body onload=\"bodyLoaded()\">\n"),
							CResources.openBinary("html/header.html").get(), streamify("<section id=\"page\">"),
							CResources.openBinary("html/prebody.html").get(), prepared.body(),
							CResources.openBinary("html/postbody.html").get(), streamify("</section></body></html>")),
					Charset.forName("utf-8")).transferTo(w);
			var body = w.toString();
			log.finer(body);
			return new RsGzip(new RsWithHeaders(
					new RsWithType(new RsCWrap(new RsUnicodeText(body)), "text/html", Charset.forName("utf-8")),
					sequencify(prepared.head(), headPrepared.head())));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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

	public static <T> Iterable<T> sequencify(final Iterable<T>... iterables) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private int i = 0;
					private Iterator<T> currentIterator = iterables[i].iterator();

					@Override
					public boolean hasNext() {
						if (currentIterator.hasNext())
							return true;
						while (!currentIterator.hasNext() && i < iterables.length - 1) {
							currentIterator = iterables[++i].iterator();
							if (currentIterator.hasNext())
								return true;
						}
						return false;
					}

					@Override
					public T next() {
						log.fine("sequence iterator %d of %d: %s. hasnext=%s".formatted(i, iterables.length - 1,
								currentIterator, currentIterator.hasNext()));
						if (currentIterator.hasNext()) {
							log.fine("normal next");
							return currentIterator.next();
						}
						log.fine("proceed to another iterator and try again");
						if (i >= iterables.length - 1)
							throw new NoSuchElementException();
						currentIterator = iterables[++i].iterator();
						return next();
					}

				};
			}
		};
	}

	@Override
	public CObject clone() {
		return new TkStaticPageWrap(sub, pagename);
	}
}