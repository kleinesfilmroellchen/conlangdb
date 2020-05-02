package klfr.conlangdb.http.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHref;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithHeaders;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import klfr.conlangdb.http.RsCWrap;

/**
 * Advanced static file server that extends TkFiles' behavior. This file take
 * will auto-detect MIME file type based on extension. It will also use the
 * Module resource loader for loading the files to enable JAR-based
 * resources.<br>
 * <h3>Cache control</h3> This Take applies a couple of cache control
 * strategies. For one, public caching of the files is allowed for up to 20
 * days. Second, a Last-Modified header contains the time of last file change as
 * recorded by the operating system. This feature only works on non-JAR
 * resources.
 */
public class TkFilesAdvanced extends CObject implements Take {
	// Third, an If-Modified-Since header is honored by returning an empty 304 (Not
	// Modified) response if the last modification time was earlier than the given
	// time.

	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(TkFilesAdvanced.class.getCanonicalName());

	/**
	 * Map that stores mappings from file extensions to MIME types, as they appear
	 * in Content-Type headers.
	 */
	private static final Map<String, String> mimeTypes = new TreeMap<String, String>();
	static {
		mimeTypes.put("js", "text/javascript");
		mimeTypes.put("css", "text/css");
		mimeTypes.put("bmp", "image/bmp");
		mimeTypes.put("cod", "image/cis-cod");
		mimeTypes.put("ras", "image/cmu-raster");
		mimeTypes.put("fif", "image/fif");
		mimeTypes.put("gif", "image/gif");
		mimeTypes.put("ief", "image/ief");
		mimeTypes.put("jpeg", "image/jpeg");
		mimeTypes.put("jpg", "image/jpeg");
		mimeTypes.put("jpe", "image/jpeg");
		mimeTypes.put("png", "image/png");
		mimeTypes.put("ico", "image/png");
		mimeTypes.put("svg", "image/svg+xml");
		mimeTypes.put("tiff", "image/tiff");
		mimeTypes.put("tif", "image/tiff");
		mimeTypes.put("mpeg", "video/mpeg");
		mimeTypes.put("mpg", "video/mpeg");
		mimeTypes.put("mpe", "video/mpeg");
		mimeTypes.put("mp4", "video/mp4");
		mimeTypes.put("ogg", "video/ogg");
		mimeTypes.put("ogv", "video/ogg");
		mimeTypes.put("qt", "video/quicktime");
		mimeTypes.put("mov", "video/quicktime");
		mimeTypes.put("viv", "video/vnd.vivo");
		mimeTypes.put("vivo", "video/vnd.vivo");
		mimeTypes.put("webm", "video/webm");
		mimeTypes.put("avi", "video/x-msvideo");
		mimeTypes.put("movie", "video/x-sgi-movie");
		mimeTypes.put("mp3", "audio/mpeg");
		mimeTypes.put("mp4", "audio/mp4");
		mimeTypes.put("ogg", "audio/ogg");
		mimeTypes.put("tsi", "audio/tsplayer");
		mimeTypes.put("vox", "audio/voxware");
		mimeTypes.put("wav", "audio/wav");
		mimeTypes.put("mid", "audio/x-midi");
		mimeTypes.put("midi", "audio/x-midi");
		mimeTypes.put("mp2", "audio/x-mpeg");
		mimeTypes.put("zip", "application/zip");
	}

	private static final Pattern extensionPt = Pattern.compile(".*?\\.(\\S+)");

	private final String path;

	/**
	 * Creates a take that serves all files on the specifed resource path. The
	 * module must have read access to this path and all of its children.
	 * 
	 * @param path The path to the resource folder that should be served, without
	 *             leading "./" and trailing "/". For all actual resources, their
	 *             HTTP request path is appended to this base path.
	 */
	public TkFilesAdvanced(String path) {
		this.path = path;
	}

	/** Public cache allowed, maximum cache age 20 days */
	public static String cacheControl = "Cache-Control: public, max-age=2592000, immutable";

	@Override
	public CObject clone() {
		return new TkFilesAdvanced(path);
	}

	@Override
	public Response act(Request request) {
		try {
			final String requestedPath = new RqHref.Base(request).href().path();
			final var maybeRequestStream = CResources.openBinary(this.path + requestedPath);
			if (maybeRequestStream.isEmpty()) {
				return new RsCWrap(HttpStatusCode.NOT_FOUND);
			}
			final List<String> headers = new LinkedList<String>();
			headers.add(cacheControl);
			// add Content-Type (auto-detect)
			final var extensionMatcher = extensionPt.matcher(requestedPath);
			if (extensionMatcher.matches()) {
				log.fine(() -> "Content type auto-detected as %s from extension .%s".formatted(
						mimeTypes.getOrDefault(extensionMatcher.group(1), "text/plain"), extensionMatcher.group(1)));
				headers.add("Content-Type: " + mimeTypes.getOrDefault(extensionMatcher.group(1), "text/plain"));
			}
			// attempt to get the last modified time for the resource - this is only
			// possible on plain files not packed into a JAR
			final var lastModified = getLastModified(this.path + requestedPath);
			if (lastModified.isPresent()) {
				log.fine(() -> "Last modification retrieved successfully for file %s%s: %s or '%s'".formatted(this.path,
						requestedPath, lastModified.get(), toHTTPTime(lastModified.get())));
				headers.add("Last-Modified: " + toHTTPTime(lastModified.get()));
			}
			// RsWithBody takes care of the content-length header
			return new RsCWrap(new RsWithHeaders(new RsWithBody(maybeRequestStream.get()), headers), HttpStatusCode.OK);
		} catch (Throwable e) {
			log.log(Level.SEVERE, "Server error on static file serve.", e);
			return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
		}
	}

	private static Optional<java.time.Instant> getLastModified(String fpath) {
		final var f = new File("./out/" + CResources.RESOURCE_PATH + "/" + fpath);
		if (!f.exists())
			return Nothing();
		log.finer("File %s exists".formatted(f));
		final var path = f.toPath();
		try {
			final var modifiedTime = Files.getLastModifiedTime(path);
			return Just(modifiedTime.toInstant());
		} catch (IOException e) {
			return Nothing();
		}
	}

	/**
	 * Converts the given instant to the HTTP time string. The time is assigned into
	 * the system timezone and then shifted into GMT as per HTTP specification.
	 * 
	 * @param time The instant to convert. Note that Instants do not contain
	 *             timezone information; the system timezone is used.
	 * @return A string with the HTTP date and time format, e.g. "Tue, 15 Nov 1994
	 *         12:45:26 GMT"
	 */
	public static String toHTTPTime(Instant time) {
		// Java Datetime API is the best designed API on the planet.
		return time.atZone(ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
	}

}