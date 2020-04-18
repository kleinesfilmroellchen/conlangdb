package klfr.conlangdb.http;

import java.io.IOException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHeaders;
import org.takes.rq.RqRequestLine;
import org.takes.rs.RsText;
import org.takes.rs.RsWithHeader;

import klfr.conlangdb.CObject;
import klfr.conlangdb.database.DatabaseCommand;
import klfr.conlangdb.database.DatabaseCommunicator;
import klfr.conlangdb.util.StringStreamUtil;

/**
 * Container for the three HTTP methods associated with the single language API.
 */
public final class TkLanguageAPI {

	public static final Pattern languageAPIPattern = Pattern.compile("/language/(\\S+)");

	/**
	 * Get method on the single language API. Returns language information in JSON
	 * format.
	 */
	public static class Get extends CObject implements Take {
		private static final Logger log = Logger.getLogger(Get.class.getCanonicalName());
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request request) {
			try {
				final var pathMatcher = languageAPIPattern.matcher(new RqRequestLine.Base(request).uri());
				// illegal path - should not happen
				if (!pathMatcher.matches())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);
				final var language = pathMatcher.group(1);
				log.fine(() -> "Requested language data for id %s".formatted(language));
				final var command = DatabaseCommand.from(con -> {
					try {
						// prepare statement
						final var stmt = con.prepareStatement(
								"select id, name, name_en as \"name-en\", description, description_en as \"description-en\", isconlang, fonturl from tlanguage where id=?;");
						stmt.setString(1, language);
						// will throw sql error when nothing was found - this behavior is desirable
						final var rset = stmt.executeQuery();
						rset.next();

						// create result object
						final var obj = new JSONObject();
						for (var key : List.of("id", "name", "name-en", "description", "description-en", "fonturl"))
							obj.put(key, Just(rset.getString(key)).orElse(""));
						obj.put("isconlang", Just(rset.getBoolean("isconlang")).orElse(false));
						rset.close();
						return Just(obj);
					} catch (SQLException e) {
						log.log(Level.SEVERE, "Server SQL exception", e);
						return Nothing();
					}
				});
				DatabaseCommunicator.submitCommand(command);
				final var languageData = command.get().orElseThrow(() -> new SQLException("language not found"));
				log.finer(() -> languageData.toString());
				return new RsCWrap(new RsJSON(languageData));
			} catch (IOException | InterruptedException | ExecutionException e) {
				log.log(Level.SEVERE, "Server exception", e);
				return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
			} catch (SQLException e) {
				log.warning("Language for %s not found".formatted(request));
				return new RsCWrap(HttpStatusCode.NOT_FOUND);
			}

		}

		@Override
		public CObject clone() {
			return new Get();
		}

	}

	public static class Post extends CObject implements Take {
		private static final Logger log = Logger.getLogger(Post.class.getCanonicalName());
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request request) {
			try {
				request = new RqBody(request);
				// parse body
				final var charset = new RqHeaders.Smart(request).single("Accept-Charset", "utf-8");
				final var bodyS = StringStreamUtil.stringify(request.body(), charset);
				final var rq = new JSONObject(bodyS);
				log.finer(() -> rq.toString());

				// parse language
				final var pathMatcher = languageAPIPattern.matcher(new RqRequestLine.Base(request).uri());
				if (!pathMatcher.matches())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);
				final var modifiedLanguage = pathMatcher.group(1);
				log.fine(() -> "Modifying language %s".formatted(modifiedLanguage));

				final var command = DatabaseCommand.from(con -> {
					try {
						// check whether the language that is to be modified exists
						final var existenceChecker = con.prepareStatement("select id from tlanguage where id=?;");
						existenceChecker.setString(1, modifiedLanguage);
						final var exists = existenceChecker.execute();
						if (exists) {
							// the language exists, do UPDATE (which may change the language id)
							// also, this may fail if there are no valid keys, but that is ok (caught below)
							final var updator = con.prepareStatement(new StringBuilder("update tlanguage set ")
									.append(String.join(", ", List.of(rq.has("id") ? "id=?" : "",
											rq.has("name") ? "name=?" : "",
											rq.has("name-en") ? "name_en=?" : "",
											rq.has("description") ? "description=?" : "",
											rq.has("description-en") ? "description_en=?" : "",
											rq.has("fonturl") ? "fonturl=?" : "",
											rq.has("config") ? "config=?" : "")
											.stream().filter(x -> !x.isBlank()).collect(Collectors.toList())))
									.append(" where id=?;").toString());
							// set all existent keys, use counter to keep track of parameter index
							var counter = 1;
							for (final var key : List.of("id", "name", "name-en", "description", "description-en",
									"fonturl", "config")) {
								if (rq.has(key))
									updator.setString(counter++, rq.getString(key));
							}
							log.finer("Parameter counter %s, query '%s'".formatted(counter, updator.toString()));
							// the where clause's language parameter is the last argument
							updator.setString(counter, modifiedLanguage);

							final var updateCount = updator.executeUpdate();
							// use the new id from the json if existent
							return updateCount > 0 ? Just(rq.optString("id", modifiedLanguage)) : Nothing();
						} else {
							// the language does not exist, do INSERT
							final var inserter = con.prepareStatement(
									"insert into tlanguage ( id, name, name_en, description, description_en, fonturl, config ) values (?, ?, ?, ?, ?, ?, ?) on conflict do nothing;");
							inserter.setString(1, rq.optString("id"));
							inserter.setString(2, rq.getString("name"));
							inserter.setString(3, rq.getString("name-en"));
							inserter.setString(4, rq.getString("description"));
							inserter.setString(5, rq.getString("description-en"));
							inserter.setString(6, rq.getString("fonturl"));
							inserter.setString(7, rq.getString("config"));

							final var insertionCount = inserter.executeUpdate();
							return insertionCount > 0 ? Just(modifiedLanguage) : Nothing();
						}
					} catch (SQLException e) {
						log.log(Level.WARNING, "SQL error, probably client-caused", e);
						return Nothing();
					} catch (JSONException e) {
						log.log(Level.WARNING, "Malformed input JSON", e);
						return Nothing();
					}
				});
				DatabaseCommunicator.submitCommand(command);
				final var maybeNewpath = command.get();
				if (maybeNewpath.isEmpty())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);

				log.fine("Successful update/insert.");
				final var newpath = "/language/" + maybeNewpath.get();
				return new RsCWrap(new RsWithHeader("Location", newpath), HttpStatusCode.NO_CONTENT);
			} catch (IOException | InterruptedException | ExecutionException e) {
				log.log(Level.SEVERE, "Server exception", e);
				return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
			} catch (JSONException e) {
				log.log(Level.WARNING, "Malformed JSON", e);
				return new RsCWrap(HttpStatusCode.BAD_REQUEST);
			} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
				log.log(Level.SEVERE, "Request encoding exception", e);
				try {
					return new RsCWrap(
							new RsText("Charset %s unknown."
									.formatted(new RqHeaders.Smart(request).single("Accept-Charset", "utf-8"))),
							HttpStatusCode.BAD_REQUEST);
				} catch (IOException e1) {
					log.log(Level.SEVERE, "Server exception", e);
					return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
				}
			}
		}

		@Override
		public CObject clone() {
			return new Post();
		}
	}

	public static class Delete extends CObject implements Take {
		private static final Logger log = Logger.getLogger(Delete.class.getCanonicalName());
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request request) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CObject clone() {
			return new Delete();
		}
	}

}
