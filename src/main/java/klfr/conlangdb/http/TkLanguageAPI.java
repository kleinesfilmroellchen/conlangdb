package klfr.conlangdb.http;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqRequestLine;

import klfr.conlangdb.CObject;
import klfr.conlangdb.database.DatabaseCommand;
import klfr.conlangdb.database.DatabaseCommunicator;

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
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request request) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CObject clone() {
			return new Get();
		}

	}

	public static class Delete extends CObject implements Take {
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request request) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CObject clone() {
			return new Get();
		}

	}

}
