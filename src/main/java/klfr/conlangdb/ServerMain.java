package klfr.conlangdb;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.takes.Response;
import org.takes.facets.fallback.Fallback;
import org.takes.facets.fallback.FbChain;
import org.takes.facets.fallback.RqFallback;
import org.takes.facets.fallback.TkFallback;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.FkTypes;
import org.takes.facets.fork.TkFork;
import org.takes.http.BkBasic;
import org.takes.http.BkParallel;
import org.takes.http.Exit;
import org.takes.http.FtBasic;
import org.takes.rs.RsHtml;
import org.takes.tk.TkFiles;
import org.takes.tk.TkWithType;

import klfr.conlangdb.http.FbFail;
import klfr.conlangdb.http.HttpStatusCode;
import klfr.conlangdb.http.TkLanguagePage;
import klfr.conlangdb.http.TkListAPI;
import klfr.conlangdb.http.TkLog;
import klfr.conlangdb.http.TkMainPage;
import klfr.conlangdb.http.TkStaticPageWrap;
import klfr.conlangdb.http.TkStatistics;
import klfr.conlangdb.http.TkTranslations;

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
			log.info("Server Main method entered. ÄÄÖÖÜÜ");
			log.config(() -> arguments.toString());

			//// Setup the Takes server architecture
			// Basic frontent using parallel threads, logging and error handling
			new FtBasic(new BkParallel(new BkBasic(new TkLog(new TkFallback(new TkFork(
					//// STATIC
					// Static JavaScript
					new FkRegex("/js/.+", new TkWithType(new TkFiles(new File("./out/res/static")), "text/javascript")),
					// Static CSS
					new FkRegex("/css/.+", new TkWithType(new TkFiles(new File("./out/res/static")), "text/css")),
					// Static images
					new FkRegex("/img/.+", new TkFiles("./out/res/static")),
					// Favicon
					new FkRegex(Pattern.quote("/favicon.ico"),
							new TkWithType(new TkFiles(new File("./out/res/static/img")), "image/png")),
					//// PAGES
					// Main page
					new FkRegex("/", new TkStaticPageWrap(new TkMainPage(), "mainpage")),
					// Languages page: depending on accept header, serve different page
					new FkRegex(Pattern.quote("/language/list"),
							new TkFork(
									new FkTypes("text/html", new TkStaticPageWrap(new TkLanguagePage(),
											new TkLanguagePage.Header(),
											"languages")),
									new FkTypes("application/json",
											new TkListAPI(TkListAPI.languageQueryBuilder, List.of("id", "name"),
													"id")))),
					//// API
					// Translation JSON maps
					new FkRegex("\\/translation\\/([a-z]{2,3})(?:\\_([A-Z]{2,3}))?", new TkTranslations()),
					// Statistics
					new FkRegex(Pattern.quote("/statistics"), new TkStatistics())),
					// Fallback for handling server errors and error codes
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