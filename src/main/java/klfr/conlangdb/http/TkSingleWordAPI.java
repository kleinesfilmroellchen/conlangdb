package klfr.conlangdb.http;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHeaders;
import org.takes.rq.RqHref;
import org.takes.rs.RsText;
import org.takes.rs.RsWithHeader;

import klfr.conlangdb.CObject;
import klfr.conlangdb.database.DatabaseCommand;
import klfr.conlangdb.database.DatabaseCommunicator;
import klfr.conlangdb.database.commands.SQLCmd;
import klfr.conlangdb.database.commands.WordAttributeDataCmd;
import klfr.conlangdb.http.util.HttpStatusCode;
import klfr.conlangdb.http.util.RqBody;
import klfr.conlangdb.http.util.RsJSON;
import klfr.conlangdb.util.Range;
import klfr.conlangdb.util.StringStreamUtil;

public class TkSingleWordAPI extends CObject {
	private static final long serialVersionUID = 1L;

	public static final Pattern singleWordAPIPtn = Pattern.compile("/word/(\\S{1,3})/(.+)");

	/**
	 * Command that retrieves the word ID belonging to a language id - romanized
	 * word text - pair.
	 */
	public static class WordIdFinderCmd extends DatabaseCommand<Long> {
		private static final long serialVersionUID = 1L;

		public final String language;
		public final String romanized;

		public WordIdFinderCmd(final String language, final String romanized) {
			super(con -> {
				try {
					final var wordIdFinder = con.prepareStatement("select id from tword where romanized=? and lid=?;");
					wordIdFinder.setString(1, romanized);
					wordIdFinder.setString(2, language);
					final var wordIdResults = wordIdFinder.executeQuery();
					// no word exists: execute INSERT
					if (!wordIdResults.next())
						return Nothing();
					return Just(wordIdResults.getLong("id"));
				} catch (SQLException e) {
					return Nothing();
				}
			});
			this.language = language;
			this.romanized = romanized;
		}

		@Override
		public Stream<Object> getArguments() {
			return Stream.of(language, romanized);
		}

		@Override
		public <U> Optional<U> getArgument(int index) {
			return index == 0 ? (Optional<U>) Just(language) : index == 1 ? (Optional<U>) Just(romanized) : Nothing();
		}

		@Override
		public DatabaseCommand<Long> clone() {
			return new WordIdFinderCmd(language, romanized);
		}

	}

	public static class Get extends CObject implements Take {
		private static final Logger log = Logger.getLogger(Get.class.getCanonicalName());
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request rq) {
			try {
				// prepare
				final var m = singleWordAPIPtn.matcher(new RqHref.Base(rq).href().path());
				if (!m.matches())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);
				final String language = m.group(1), word = m.group(2);
				log.fine(() -> f("lang=%s word=%s", language, word));
				final String translationLanguage = new RqHref.Smart(rq).single("to", "");

				// command
				final var mainCmd = DatabaseCommand.from(con -> {
					final var stmt = con.prepareStatement("select romanized, native as \"text\", id, "
							+ "ARRAY(select definition from tdefinition where TWord.ID=TDefinition.WID) as definitions "
							+ "from tword where lid=? and romanized=?;");
					stmt.setString(1, language);
					stmt.setString(2, word);
					return Just(stmt.executeQuery());
				});
				DatabaseCommunicator.submitCommand(mainCmd);

				// retrieve main data
				final var maybeRset = mainCmd.get();
				if (maybeRset.isEmpty())
					return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
				final var rset = maybeRset.get();
				if (!rset.next())
					return new RsCWrap(HttpStatusCode.NOT_FOUND);

				// get word ID
				final var wordID = rset.getLong("id");
				log.finer(() -> String.valueOf(wordID));
				final var cmds = new LinkedList<DatabaseCommand<ResultSet>>();
				// submit other commands that depend on word id.
				if (!translationLanguage.isEmpty()) {
					final var translationsCmd = DatabaseCommand.from(con -> {
						final var stmt = con.prepareStatement(
								"select romanized, native as text, RelTranslation.description as description from TWord translation "
										+ "join RelTranslation on (translation.ID=RelTranslation.WIDOne or translation.ID=RelTranslation.WIDTwo) "
										+ "where (?=RelTranslation.WIDOne or ?=RelTranslation.WIDTwo) "
										+ "and translation.LID=?;");
						stmt.setLong(1, wordID);
						stmt.setLong(2, wordID);
						stmt.setString(3, translationLanguage);
						return Just(stmt.executeQuery());
					});
					DatabaseCommunicator.submitCommand(translationsCmd);
					cmds.add(translationsCmd);
				}
				final var attributeCmd = new WordAttributeDataCmd(wordID);
				DatabaseCommunicator.submitCommand(attributeCmd);
				cmds.add(attributeCmd);

				// convert main data to JSON
				final var obj = new JSONObject();
				for (var i : new Range(1, rset.getMetaData().getColumnCount()))
					obj.put(rset.getMetaData().getColumnLabel(i),
							DatabaseCommunicator.javaType(rset.getObject(i)).orElse(null));

				// convert translation and attribute data to JSON
				final List<JSONArray> subArrays = cmds.stream().map(subCmd -> {
					try {
						final var maybeSubRset = subCmd.get();
						if (maybeSubRset.isEmpty())
							return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
						final var subRset = maybeSubRset.get();
						final JSONArray subArr = new JSONArray();
						while (subRset.next()) {
							final var tobj = new JSONObject();
							for (var i : new Range(1, subRset.getMetaData().getColumnCount()))
								tobj.put(subRset.getMetaData().getColumnLabel(i),
										DatabaseCommunicator.javaType(subRset.getObject(i)).orElse(null));
							subArr.put(tobj);
						}
						log.finer(() -> subArr.toString());
						return subArr;
					} catch (JSONException | InterruptedException | ExecutionException | SQLException e) {
						log.log(Level.SEVERE, "", e);
						return new JSONArray();
					}
				})
						// Note that this map is technically pointless, but Java cannot infer the return
						// type of the above lambda for some reason.
						.map(x -> (JSONArray) x).collect(Collectors.<JSONArray>toList());
				if (!translationLanguage.isEmpty())
					obj.put("translations", subArrays.remove(0));
				obj.put("attributes", subArrays.get(0));

				// send response
				return new RsCWrap(new RsJSON(obj));
			} catch (IOException | InterruptedException | ExecutionException | SQLException e) {
				log.log(Level.SEVERE, "", e);
				return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
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

				// get word and language data
				final var m = singleWordAPIPtn.matcher(new RqHref.Base(request).href().path());
				if (!m.matches())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);
				final String language = m.group(1), word = m.group(2);
				log.fine(() -> f("lang=%s word=%s", language, word));

				// parse body
				final var charset = new RqHeaders.Smart(request).single("Accept-Charset", "utf-8");
				final var bodyS = StringStreamUtil.stringify(request.body(), charset);
				final var rq = new JSONObject(bodyS);

				// try to get word ID
				final var wordIdFinder = new WordIdFinderCmd(language, word);
				DatabaseCommunicator.submitCommand(wordIdFinder);
				final var mWordId = wordIdFinder.get();

				HttpStatusCode scode;
				DatabaseCommand<ResultSet> cmd;
				if (mWordId.isEmpty()) {
					// no word exists: execute INSERT
					cmd = DatabaseCommand.from(con -> {
						con.setAutoCommit(true);
						final var insertor = con.prepareStatement(
								"insert into tword (native, romanized, lid) values (?, ?, ?) returning romanized, lid, id;");
						insertor.setString(1, rq.optString("text", null));
						insertor.setString(2, word);
						insertor.setString(3, language);
						log.fine(() -> f("DATABASE Add word: %s", insertor));

						// execute and return
						final var rset = insertor.executeQuery();
						return Just(rset);
					});
					scode = HttpStatusCode.CREATED;
				} else {
					// word exists: execute UPDATE on word id
					final var wordId = mWordId.get();
					log.finer(() -> f("Word id=%s", wordId));

					cmd = DatabaseCommand.from(con -> {
						con.setAutoCommit(true);
						final var updator = con.prepareStatement(
								new StringBuilder("update tword set ").append(rq.has("text") ? "native=?" : "")
										.append(rq.has("romanized") ? ", romanized=?" : "")
										.append(rq.has("language") ? ", lid=?" : "")
										.append(" where id=? returning romanized, lid;").toString());
						var counter = 1;
						for (final var key : List.of("text", "romanized", "language"))
							if (rq.has(key))
								updator.setString(counter++, rq.getString(key));
						updator.setLong(counter, wordId);
						log.fine(() -> f("DATABASE Modify word: %s", updator));

						// execute and return
						final var rset = updator.executeQuery();
						return Just(rset);
					});
					scode = HttpStatusCode.CREATED;
				}

				DatabaseCommunicator.submitCommand(cmd);

				// construct location from command return value and exit
				final ResultSet newLocationRset = cmd.get().orElseThrow(() -> new JSONException("fake"));
				if (!newLocationRset.next())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);
				return new RsCWrap(
						new RsWithHeader("Location", String.format("/word/%s/%s",
								URLEncoder.encode(newLocationRset.getString("lid"), Charset.forName("utf-8")),
								URLEncoder.encode(newLocationRset.getString("romanized"), Charset.forName("utf-8")))),
						scode);

			} catch (IOException | ExecutionException | InterruptedException e) {
				return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
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
			} catch (JSONException | SQLException e) {
				return new RsCWrap(HttpStatusCode.BAD_REQUEST);
			}
		}

		@Override
		public CObject clone() {
			return new Post();
		}
	}

	public static class Delete extends CObject implements Take {
		private static final long serialVersionUID = 1L;
		private static final Logger log = Logger.getLogger(Delete.class.getCanonicalName());

		@Override
		public Response act(Request request) {
			try {
				request = new RqBody(request);

				// get word and language data
				final var m = singleWordAPIPtn.matcher(new RqHref.Base(request).href().path());
				if (!m.matches())
					return new RsCWrap(HttpStatusCode.BAD_REQUEST);
				final String language = m.group(1), word = m.group(2);
				log.fine(() -> f("lang=%s word=%s", language, word));

				final var delcount = DatabaseCommunicator.submitCommand(DatabaseCommand.from(con -> {
					final var stmt = con.prepareStatement("delete from tword where lid=? and romanized=?;");
					stmt.setString(1, language);
					stmt.setString(2, word);
					return Just(stmt.executeUpdate());
				})).get();

				if (delcount.isEmpty())
					return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
				if (delcount.get() == 0)
					return new RsCWrap(HttpStatusCode.NOT_FOUND);
				return new RsCWrap(HttpStatusCode.NO_CONTENT);

			} catch (IOException | InterruptedException | ExecutionException e) {
				return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
			}

		}

		@Override
		public CObject clone() {
			return new Delete();
		}

	}

	@Override
	public CObject clone() {
		return new TkSingleWordAPI();
	}

}
