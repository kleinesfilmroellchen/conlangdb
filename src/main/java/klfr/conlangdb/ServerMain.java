package klfr.conlangdb;

import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.takes.Response;
import org.takes.facets.fallback.Fallback;
import org.takes.facets.fallback.FbChain;
import org.takes.facets.fallback.RqFallback;
import org.takes.facets.fallback.TkFallback;
import org.takes.facets.fork.FkMethods;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.FkTypes;
import org.takes.facets.fork.TkFork;
import org.takes.http.BkBasic;
import org.takes.http.BkParallel;
import org.takes.http.Exit;
import org.takes.http.FtBasic;
import org.takes.rs.RsHtml;

import klfr.conlangdb.http.TkDictionaryPage;
import klfr.conlangdb.http.TkFontProvider;
import klfr.conlangdb.http.TkLanguageAPI;
import klfr.conlangdb.http.TkLanguageListPage;
import klfr.conlangdb.http.TkLanguagePage;
import klfr.conlangdb.http.TkListAPI;
import klfr.conlangdb.http.TkMainPage;
import klfr.conlangdb.http.TkSingleWordAPI;
import klfr.conlangdb.http.TkSingleWordPage;
import klfr.conlangdb.http.TkStaticPageWrap;
import klfr.conlangdb.http.TkStatistics;
import klfr.conlangdb.http.TkTranslations;
import klfr.conlangdb.http.util.FbFail;
import klfr.conlangdb.http.util.HttpStatusCode;
import klfr.conlangdb.http.util.TkFilesAdvanced;
import klfr.conlangdb.http.util.TkLog;

/**
 * Main class of the server.
 */
public class ServerMain extends CObject {
	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(ServerMain.class.getCanonicalName());

	/**
	 * Simple class that holds command line arguments in object form.
	 */
	public static class Arguments extends CObject {
		private static final long serialVersionUID = 1L;

		/** Port on which the server will listen. */
		public int port = 8001;
		public String databaseName = "conlangdb";
		/** PostgreSQL database user */
		public String databaseUser = "conlang";
		/** PostgreSQL database user password */
		public String databasePassword = "planlingvo";

		public String errorMessage = null;

		public String toString() {
			return f("Arguments(port=%d,error='%s',db=%s@%s,passwd='%s')", port, errorMessage, databaseUser,
					databaseName, databasePassword);
		}

		@Override
		public CObject clone() {
			Arguments nw = new Arguments();
			nw.port = this.port;
			nw.errorMessage = this.errorMessage;
			return nw;
		}
	}

	private final Arguments arguments;

	public ServerMain(Arguments arg) {
		this.arguments = arg;
	}

	/**
	 * Main method of the HTTP server.
	 */
	public void start() {
		try {
			log.info("Server Main method entered. Unicode test: ÄÖÜßЛこれ");
			log.config(() -> arguments.toString());

			//// Setup the Takes server architecture
			// Basic frontent using parallel threads, logging and error handling
			new FtBasic(new BkParallel(new BkBasic(new TkLog(new TkFallback(new TkFork(
					//// STATIC
					// Static JavaScript
					new FkRegex("/js/.+", new TkFilesAdvanced("static")),
					// Static CSS
					new FkRegex("/css/.+", new TkFilesAdvanced("static")),
					// Static images
					new FkRegex("/img/.+", new TkFilesAdvanced("static")),
					// Favicon
					new FkRegex(Pattern.quote("/favicon.ico"), new TkFilesAdvanced("static/img")),
					//// PAGES / PAGE-API COMBINATION
					// Main page
					new FkRegex("/", new TkStaticPageWrap(new TkMainPage(), "mainpage")),
					// Language list page/api
					new FkRegex(Pattern.quote("/language/list"), new TkFork(
							new FkTypes("text/html",
									new TkStaticPageWrap(new TkLanguageListPage(), new TkLanguageListPage.Header(),
											"languages")),
							new FkTypes("application/json",
									new TkListAPI(TkListAPI.languageQueryBuilder, List.of("id", "name"), "id")))),
					// Single language page/api
					new FkRegex(TkLanguageAPI.languageAPIPattern,
							new TkFork(
									new FkMethods("GET",
											new TkFork(
													new FkTypes("text/html",
															new TkStaticPageWrap(new TkLanguagePage(),
																	new TkLanguagePage.Headers(), "language")),
													new FkTypes("application/json", new TkLanguageAPI.Get()))),
									new FkMethods("POST", new TkLanguageAPI.Post()),
									new FkMethods("DELETE", new TkLanguageAPI.Delete()))),
					// Word list page/api (WIP)
					new FkRegex(Pattern.quote("/word/list"),
							new TkFork(
									new FkTypes("text/html",
											new TkStaticPageWrap(new TkDictionaryPage(), new TkDictionaryPage.Headers(),
													"dictionary")),
									new FkTypes("application/json",
											new TkListAPI(TkListAPI.wordQueryBuilder, List.of("text", "translations"),
													"romanized")))),
					// single word page/api (WIP)
					new FkRegex(TkSingleWordAPI.singleWordAPIPtn,
							new TkFork(
									new FkMethods("GET",
											new TkFork(
													new FkTypes("text/html",
															new TkStaticPageWrap(new TkSingleWordPage(),
																	new TkSingleWordPage.Headers(), "word")),
													new FkTypes("application/json", new TkSingleWordAPI.Get()))),
									new FkMethods("POST", new TkSingleWordAPI.Post()),
									new FkMethods("DELETE", new TkSingleWordAPI.Delete()))),
					//// API
					// Translation JSON maps
					new FkRegex("\\/translation\\/([a-z]{2,3})(?:\\_([A-Z]{2,3}))?", new TkTranslations()),
					new FkRegex(TkFontProvider.requestPath, new TkFontProvider()),
					// Statistics
					new FkRegex(Pattern.quote("/statistics"), new TkStatistics())),
					//// Fallback for handling server errors and error codes
					new FbChain(new FbFail(HttpStatusCode.NOT_FOUND), new FbFail(HttpStatusCode.METHOD_UNALLOWED),
							new FbFail(HttpStatusCode.BAD_REQUEST), new FbFail(HttpStatusCode.INTERNAL_SERVER_ERROR),
							new Fallback() {
								public org.takes.misc.Opt<Response> route(final RqFallback req) {
									log.severe("Server exception " + req);
									return new org.takes.misc.Opt.Single<Response>(
											new RsHtml("oops, something went terribly wrong!"));
								}
							})))),
					10),
					// Start server on given port and run it forever
					arguments.port).start(Exit.NEVER);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		try {
			while (true)
				Thread.sleep(20000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public CObject clone() {
		return new ServerMain(this.arguments);
	}
}