package klfr.conlangdb.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHeaders;
import org.takes.rs.RsGzip;
import org.takes.rs.RsWithHeaders;
import org.takes.rs.RsWithType;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import klfr.conlangdb.util.StringStreamUtil;

/**
 * Wrapper for adding standard HTML head with frontend scripting as well as body
 * headers and footers to any page of the webapp.
 */
public class TkStaticPageWrap extends CObject implements Take {
	private static final long serialVersionUID = 1L;

	public static final Logger log = Logger.getLogger(TkStaticPageWrap.class.getCanonicalName());

	private final Take sub;
	private final Optional<Take> headsub;
	private final String pagename;

	/**
	 * @param sub      The primary take that provides a response.
	 * @param pagename ID of the page, used to provide page title and help texts.
	 */
	public TkStaticPageWrap(final Take sub, final String pagename) {
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
	public TkStaticPageWrap(final Take sub, final Take headsub, final String pagename) {
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

			var body = StringStreamUtil.stringify(StringStreamUtil.sequencify(
					StringStreamUtil.streamify("<!DOCTYPE html>\n<html lang=\"" + language
							+ "\"><head>\n\t<meta name=\"pageid\" content=\"" + pagename + "\">\n"),
					CResources.openBinary("html/head.html").get(), headPrepared.body(),
					StringStreamUtil.streamify("</head><body>\n"), CResources.openBinary("html/header.html").get(),
					StringStreamUtil.streamify("<section id=\"page\">"),
					CResources.openBinary("html/prebody.html").get(), prepared.body(),
					CResources.openBinary("html/postbody.html").get(),
					StringStreamUtil.streamify("</section></body></html>")));
			log.finer(body);
			return new RsGzip(new RsWithHeaders(
					new RsWithType(new RsCWrap(new RsUnicodeText(body)), "text/html", Charset.forName("utf-8")),
					StringStreamUtil.sequencify(prepared.head(), headPrepared.head())));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public CObject clone() {
		return new TkStaticPageWrap(sub, pagename);
	}
}